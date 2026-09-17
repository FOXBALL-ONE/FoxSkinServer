package top.foxball.foxskinserver.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

/**
 * 记录用户凭据的最后变更时间。
 *
 * access token 是无状态 JWT，无法逐个作废，因此在 Redis 里存一个秒级时间戳：
 * 签发时间早于该时间戳的 token 一律视为失效，改密码后旧 token 立即不可用。
 */
@Repository
class AccessTokenRevocationStore(private val redis: StringRedisTemplate) {
    private fun key(userId: Long) = "auth:credentials-changed-at:$userId"
    
    fun markCredentialsChanged(userId: Long, atEpochSeconds: Long, ttlSeconds: Long) {
        redis.opsForValue().set(key(userId), atEpochSeconds.toString(), Duration.ofSeconds(ttlSeconds))
    }
    
    fun credentialsChangedAt(userId: Long): Long? = redis.opsForValue().get(key(userId))?.toLongOrNull()
}
