package top.foxball.foxskinserver.security

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

/**
 * Yggdrasil 令牌和 join 凭证的共享缓存。
 *
 * 令牌不能放入 JWT：外置登录要求服务端能够主动吊销令牌，并且多实例部署时
 * 客户端的 join 与服务端的 hasJoined 必须看到同一份一次性状态。
 */
@Repository
class YggdrasilTokenStore(private val redis: StringRedisTemplate) {
    private val log = LoggerFactory.getLogger(YggdrasilTokenStore::class.java)
    private val fieldEncoder = Base64.getUrlEncoder().withoutPadding()
    private val fieldDecoder = Base64.getUrlDecoder()
    
    data class Token(
        val accessToken: String,
        val clientToken: String,
        val userId: Long,
        val profileId: UUID?,
        val issuedAt: Long,
        val expiresAt: Long,
        val refreshExpiresAt: Long,
    )

    data class JoinCredential(
        val accessToken: String,
        val profileId: UUID,
        val clientIp: String?,
        val userId: Long = 0,
        val external: Boolean = false,
        val username: String? = null,
    )
    
    private fun tokenKey(accessToken: String) = "yggdrasil:token:$accessToken"
    private fun currentTokenKey(userId: Long) = "yggdrasil:user-token:$userId"
    private fun joinKey(serverId: String) = "yggdrasil:join:$serverId"
    
    private fun encodeField(value: String): String =
        fieldEncoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    
    private fun decodeField(value: String): String? =
        runCatching { String(fieldDecoder.decode(value), StandardCharsets.UTF_8) }.getOrNull()
    
    private fun encode(token: Token): String = listOf(
        token.clientToken,
        token.userId.toString(),
        token.profileId?.toString().orEmpty(),
        token.issuedAt.toString(),
        token.expiresAt.toString(),
        token.refreshExpiresAt.toString(),
    ).joinToString(".", transform = ::encodeField)
    
    fun save(token: Token) {
        val encoded = encode(token)
        val ttl = ((token.refreshExpiresAt - System.currentTimeMillis() / 1000).coerceAtLeast(1))
        redis.execute(
            ISSUE_TOKEN,
            listOf(tokenKey(token.accessToken), currentTokenKey(token.userId)),
            encoded,
            ttl.toString(),
            token.accessToken,
        )
        log.debug("Yggdrasil token 保存: user_id={}, profile_id={}, ttl_seconds={}", token.userId, token.profileId, ttl)
    }
    
    /** 刷新令牌时原子地校验当前令牌并替换，避免并发 refresh 产生两张有效令牌。 */
    fun rotate(oldAccessToken: String, token: Token): Boolean {
        val ttl = ((token.refreshExpiresAt - System.currentTimeMillis() / 1000).coerceAtLeast(1))
        val result = redis.execute(
            ROTATE_TOKEN,
            listOf(tokenKey(oldAccessToken), tokenKey(token.accessToken), currentTokenKey(token.userId)),
            encode(token),
            ttl.toString(),
            token.accessToken,
            oldAccessToken,
        ) ?: 0L
        log.debug("Yggdrasil token 轮换: user_id={}, success={}", token.userId, result == 1L)
        return result == 1L
    }
    
    fun find(accessToken: String): Token? {
        if (accessToken.isBlank()) return null
        val raw = redis.opsForValue().get(tokenKey(accessToken)) ?: run {
            log.debug("Yggdrasil token 未找到")
            return null
        }
        // 兼容首版实现使用的明文分隔格式，避免滚动发布时立即踢出所有旧登录。
        val fields = if (raw.contains('.')) raw.split('.').map(::decodeField) else raw.split('|')
        if (fields.size != 6) return null
        if (fields.any { it == null }) return null
        val token = runCatching {
            Token(
                accessToken = accessToken,
                clientToken = fields[0]!!,
                userId = fields[1]!!.toLong(),
                profileId = fields[2]!!.takeIf { it.isNotBlank() }?.let(UUID::fromString),
                issuedAt = fields[3]!!.toLong(),
                expiresAt = fields[4]!!.toLong(),
                refreshExpiresAt = fields[5]!!.toLong(),
            )
        }.getOrNull()
        if (token == null) {
            log.warn("Yggdrasil token 格式无效")
            return null
        }
        
        // Redis TTL 是最终兜底；读取时再次校验时间，避免时钟漂移或旧格式缓存继续被使用。
        if (token.refreshExpiresAt <= System.currentTimeMillis() / 1000) {
            redis.delete(tokenKey(accessToken))
            redis.execute(
                COMPARE_AND_DELETE,
                listOf(currentTokenKey(token.userId)),
                accessToken,
            )
            return null
        }
        return token
    }
    
    fun revoke(accessToken: String) {
        find(accessToken)?.let { token ->
            redis.delete(tokenKey(accessToken))
            redis.execute(
                COMPARE_AND_DELETE,
                listOf(currentTokenKey(token.userId)),
                accessToken,
            )
        } ?: redis.delete(tokenKey(accessToken))
        log.debug("Yggdrasil token 已撤销")
    }
    
    fun current(userId: Long): String? = redis.opsForValue().get(currentTokenKey(userId))
    
    /** 登记 join 成功的 Profile；hasJoined 读取后会原子删除，保证一次性消费。 */
    fun saveJoin(serverId: String, accessToken: String, profileId: UUID, clientIp: String?, ttlSeconds: Long) {
        val value = listOf(accessToken, profileId.toString(), clientIp.orEmpty(), "0", "0", "")
            .joinToString(".", transform = ::encodeField)
        redis.opsForValue()
            .set(joinKey(serverId), value, Duration.ofSeconds(ttlSeconds.coerceAtLeast(1)))
        log.debug("Yggdrasil join 凭证保存: profile_id={}, ttl_seconds={}", profileId, ttlSeconds)
    }

    fun findJoin(serverId: String): JoinCredential? =
        redis.opsForValue().get(joinKey(serverId))?.let { value ->
            val fields = value.split('.').map(::decodeField)
            if ((fields.size == 3 || fields.size == 5 || fields.size == 6) && fields.all { it != null }) {
                runCatching {
                    JoinCredential(
                        fields[0]!!,
                        UUID.fromString(fields[1]!!),
                        fields[2]!!.ifBlank { null },
                        fields.getOrNull(3)?.toLongOrNull() ?: 0,
                        fields.getOrNull(4) == "1",
                        fields.getOrNull(5)?.ifBlank { null },
                    )
                }.getOrNull()
            } else null
        }
    
    /** 仅当缓存中的 Profile 与预期值一致时原子删除，避免错误用户名抢先消费凭证。 */
    fun consumeJoin(serverId: String, credential: JoinCredential): Boolean {
        val deleted = redis.execute(
            COMPARE_AND_DELETE,
            listOf(joinKey(serverId)),
            encodeJoin(credential),
        ) ?: 0L
        val consumed = deleted == 1L
        log.debug("Yggdrasil join 凭证消费: profile_id={}, success={}", credential.profileId, consumed)
        return consumed
    }

    private fun encodeJoin(credential: JoinCredential): String = listOf(
        credential.accessToken.orEmpty(),
        credential.profileId.toString(),
        credential.clientIp.orEmpty(),
        credential.userId.toString(),
        if (credential.external) "1" else "0",
        credential.username.orEmpty(),
    ).joinToString(".", transform = ::encodeField)

    fun saveExternalJoin(serverId: String, profileId: UUID, username: String, userId: Long, clientIp: String?, ttlSeconds: Long) {
        val credential = JoinCredential("", profileId, clientIp, userId, true, username)
        redis.opsForValue().set(
            joinKey(serverId),
            encodeJoin(credential),
            Duration.ofSeconds(ttlSeconds.coerceAtLeast(1)),
        )
        log.debug("Yggdrasil 正版 join 凭证保存: profile_id={}, ttl_seconds={}", profileId, ttlSeconds)
    }
    
    /** 仅删除正版来源的一次性 join 凭证；FoxSkin 账号凭证不受开关影响。 */
    fun clearExternalJoins(): Int {
        var deleted = 0
        val keys = redis.keys("yggdrasil:join:*").orEmpty()
        for (key in keys) {
            val fields = (redis.opsForValue().get(key) ?: continue).split('.').map(::decodeField)
            if (fields.size >= 5 && fields.getOrNull(4) == "1") {
                redis.delete(key)
                deleted++
            }
        }
        if (deleted > 0) log.info("Yggdrasil 正版 join 凭证已清空: count={}", deleted)
        return deleted
    }

    /** 解绑或强制取回后，撤销仍待消费的指定正版角色 join 凭证。 */
    fun revokeExternalJoinsForProfiles(profileIds: Set<UUID>): Int {
        if (profileIds.isEmpty()) return 0
        var deleted = 0
        val keys = redis.keys("yggdrasil:join:*").orEmpty()
        for (key in keys) {
            val fields = (redis.opsForValue().get(key) ?: continue).split('.').map(::decodeField)
            val profileId = fields.getOrNull(1)?.let(UUID::fromString)
            if (fields.size >= 5 && fields.getOrNull(4) == "1" && profileId in profileIds) {
                redis.delete(key)
                deleted++
            }
        }
        if (deleted > 0) log.info("Yggdrasil 指定正版 join 凭证已撤销: profile_count={}, count={}", profileIds.size, deleted)
        return deleted
    }
    companion object {
        private val ISSUE_TOKEN = DefaultRedisScript<Long>(
            """
            local old = redis.call('get', KEYS[2])
            if old then redis.call('del', 'yggdrasil:token:' .. old) end
            redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
            redis.call('set', KEYS[2], ARGV[3], 'EX', ARGV[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        
        private val ROTATE_TOKEN = DefaultRedisScript<Long>(
            """
            if redis.call('exists', KEYS[1]) == 0 then return 0 end
            if redis.call('get', KEYS[3]) ~= ARGV[4] then return 0 end
            redis.call('del', KEYS[1])
            redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[2])
            redis.call('set', KEYS[3], ARGV[3], 'EX', ARGV[2])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        
        private val COMPARE_AND_DELETE = DefaultRedisScript<Long>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
