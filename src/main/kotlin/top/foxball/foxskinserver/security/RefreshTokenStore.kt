package top.foxball.foxskinserver.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RefreshTokenStore(private val redis: StringRedisTemplate) {
    private fun key(jti: String) = "auth:refresh:$jti"

    fun save(jti: String, userId: Long, ttlSeconds: Long) {
        redis.opsForValue().set(key(jti), userId.toString(), Duration.ofSeconds(ttlSeconds))
    }

    fun consume(jti: String, userId: Long): Boolean {
        val value = redis.opsForValue().getAndDelete(key(jti)) ?: return false
        if (value != userId.toString()) return false
        return true
    }

    fun revoke(jti: String) {
        redis.delete(key(jti))
    }
}
