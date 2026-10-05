package top.foxball.foxskinserver.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** 对 TOTP 第二步按用户和客户端地址做共享滑动窗口限流。 */
@Repository
class TotpLoginRateLimiter(private val redis: StringRedisTemplate) {
    fun tryAcquire(userId: Long, clientAddress: String): Boolean {
        val identity = "$userId:${clientAddress.ifBlank { "unknown" }}"
        val key = "auth:totp-rate:${digest(identity)}"
        val result = redis.execute(
            ATTEMPT,
            listOf(key),
            System.currentTimeMillis().toString(),
            WINDOW_MILLIS.toString(),
            MAX_ATTEMPTS.toString(),
            UUID.randomUUID().toString(),
        ) ?: 0L
        return result == 1L
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L
        const val MAX_ATTEMPTS = 10L
        val ATTEMPT = DefaultRedisScript<Long>(
            """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('zremrangebyscore', KEYS[1], 0, now - window)
            local count = redis.call('zcard', KEYS[1])
            if count >= limit then
                redis.call('pexpire', KEYS[1], window)
                return 0
            end
            redis.call('zadd', KEYS[1], now, ARGV[4])
            redis.call('pexpire', KEYS[1], window)
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
