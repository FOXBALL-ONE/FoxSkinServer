package top.foxball.foxskinserver.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import top.foxball.foxskinserver.config.MojangProperties
import top.foxball.foxskinserver.handler.ForbiddenException
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

data class VerifiedMojangProfile(val id: UUID, val name: String)

/** 只负责使用 Minecraft access token 校验官方 profile，不保存或返回原始 token。 */
@Service
class MojangVerifier(
    private val properties: MojangProperties,
    private val stateService: MojangAuthenticationStateService,
    private val redis: StringRedisTemplate,
    builder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) {
    private val client = builder.build()

    fun verify(accessToken: String): VerifiedMojangProfile {
        stateService.requireEnabled()
        if (accessToken.isBlank() || accessToken.length > MAX_TOKEN_LENGTH) {
            throw ForbiddenException("正版登录令牌无效")
        }
        val cacheKey = cacheKey(accessToken)
        redis.opsForValue().get(cacheKey)?.let { cached ->
            runCatching { objectMapper.readValue(cached, VerifiedMojangProfile::class.java) }
                .getOrNull()?.let { return it }
        }

        try {
            client.get().uri(properties.entitlementUrl)
                .headers { it.setBearerAuth(accessToken) }
                .retrieve().toBodilessEntity()
        } catch (_: RestClientException) {
            throw ForbiddenException("该账号没有 Minecraft Java 版授权")
        }
        val body = try {
            client.get().uri(properties.profileUrl)
                .headers { it.setBearerAuth(accessToken) }
                .retrieve().body(String::class.java).orEmpty()
        } catch (_: RestClientException) {
            throw ForbiddenException("正版账号校验服务暂不可用")
        }
        val profile = try {
            val fields = objectMapper.readValue(body, Map::class.java)
            val rawId = fields["id"]?.toString()?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }
                ?: throw IllegalArgumentException()
            val name = fields["name"]?.toString()?.takeIf { it.matches(Regex("[A-Za-z0-9_]{3,16}")) }
                ?: throw IllegalArgumentException()
            VerifiedMojangProfile(compactUuidToUuid(rawId), name)
        } catch (_: Exception) {
            throw ForbiddenException("正版账号资料无效或未拥有 Minecraft Java 版")
        }
        redis.opsForValue().set(
            cacheKey,
            objectMapper.writeValueAsString(profile),
            Duration.ofSeconds(properties.cacheSeconds.coerceIn(1, 300)),
        )
        return profile
    }

    private fun compactUuidToUuid(value: String): UUID = UUID.fromString(
        value.replaceFirst(Regex("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})"), "$1-$2-$3-$4-$5"),
    )

    /** 缓存键只保存 SHA-256 摘要，不保存完整 access token。 */
    private fun cacheKey(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.UTF_8))
        return "mojang:profile:" + digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val MAX_TOKEN_LENGTH = 4096
    }
}