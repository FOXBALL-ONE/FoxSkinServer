package top.foxball.foxskinserver.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Yggdrasil 认证接口的账号级节流器。
 *
 * Blessing Skin 使用“同一账号两次请求之间至少间隔一段时间”的策略，
 * 这里用 Redis Lua 保证多实例部署时检查和写入时间戳不可被并发请求穿透。
 */
@Repository
class YggdrasilRateLimiter(private val redis: StringRedisTemplate) {
    fun retryAfterMillis(identity: String, intervalMillis: Long): Long {
        if (identity.isBlank() || intervalMillis <= 0) return 0
        val now = System.currentTimeMillis()
        val key = "yggdrasil:throttle:${digest(identity)}"
        return redis.execute(
            THROTTLE,
            listOf(key),
            now.toString(),
            intervalMillis.toString(),
            THROTTLE_KEY_TTL_MILLIS.toString(),
        ) ?: 0L
    }
    
    private fun digest(value: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
    )
    
    companion object {
        private const val THROTTLE_KEY_TTL_MILLIS = 3_600_000L
        private val THROTTLE = DefaultRedisScript<Long>(
            """
            local previous = redis.call('get', KEYS[1])
            if previous then
                local retry = tonumber(ARGV[2]) - (tonumber(ARGV[1]) - tonumber(previous))
                if retry > 0 then return retry end
            end
            redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[3])
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
