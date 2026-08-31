package top.foxball.foxskinserver.security

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.config.JwtProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class JwtClaims(val subject: Long, val tokenId: String, val type: String, val expiresAt: Long)

/** 使用 JDK HMAC-SHA256 实现的最小 JWT 服务，避免引入额外运行时依赖。 */
@Service
class JwtService(
    private val properties: JwtProperties,
    private val objectMapper: ObjectMapper,
) {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun createAccessToken(user: AuthenticatedUser): String = createToken(user.userId, "access", properties.access.ttlSeconds)

    fun createRefreshToken(user: AuthenticatedUser): String = createToken(user.userId, "refresh", properties.refresh.ttlSeconds)

    fun parse(token: kotlin.String): JwtClaims? {
        return try {
            val parts = token.split('.')
            if (parts.size != 3) return null
            val signed = "${parts[0]}.${parts[1]}"
            val expected = sign(signed)
            if (!MessageDigest.isEqual(expected.toByteArray(StandardCharsets.US_ASCII), parts[2].toByteArray(StandardCharsets.US_ASCII))) return null
            val payload: Map<String, Any> = objectMapper.readValue(decoder.decode(parts[1]), object : TypeReference<Map<String, Any>>() {})
            val subject = payload["sub"]?.toString()?.toLongOrNull() ?: return null
            val jti = payload["jti"]?.toString() ?: return null
            val type = payload["typ"]?.toString() ?: return null
            val expires = payload["exp"]?.toString()?.toLongOrNull() ?: return null
            if (expires <= System.currentTimeMillis() / 1000) return null
            JwtClaims(subject, jti, type, expires)
        } catch (_: Exception) {
            null
        }
    }

    private fun createToken(subject: Long, type: String, ttlSeconds: Long): String {
        require(ttlSeconds > 0) { "JWT TTL must be positive" }
        val now = System.currentTimeMillis() / 1000
        val header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray())
        val payload = objectMapper.writeValueAsBytes(mapOf("sub" to subject, "jti" to UUID.randomUUID().toString(), "typ" to type, "iat" to now, "exp" to now + ttlSeconds))
        val body = "$header.${encoder.encodeToString(payload)}"
        return "$body.${sign(body)}"
    }

    private fun sign(value: String): String = encoder.encodeToString(
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(properties.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            doFinal(value.toByteArray(StandardCharsets.US_ASCII))
        }
    )
}
