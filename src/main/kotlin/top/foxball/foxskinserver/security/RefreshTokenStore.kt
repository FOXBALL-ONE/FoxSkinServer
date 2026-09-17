package top.foxball.foxskinserver.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RefreshTokenStore(private val redis: StringRedisTemplate) {
    private fun key(jti: String) = "auth:refresh:$jti"
    
    /** 用户维度的 jti 索引，改密码时才能一次性吊销该用户全部 refresh token。 */
    private fun userIndexKey(userId: Long) = "auth:user-refresh:$userId"
    
    fun save(jti: String, userId: Long, ttlSeconds: Long) {
        val ttl = Duration.ofSeconds(ttlSeconds)
        redis.opsForValue().set(key(jti), userId.toString(), ttl)
        redis.opsForSet().add(userIndexKey(userId), jti)
        redis.expire(userIndexKey(userId), ttl)
    }
    
    fun consume(jti: String, userId: Long): Boolean {
        val value = redis.opsForValue().getAndDelete(key(jti)) ?: return false
        redis.opsForSet().remove(userIndexKey(userId), jti)
        if (value != userId.toString()) return false
        return true
    }
    
    fun revoke(jti: String) {
        val tokenKey = key(jti)
        val userId = redis.opsForValue().get(tokenKey)?.toLongOrNull()
        redis.delete(tokenKey)
        if (userId != null) redis.opsForSet().remove(userIndexKey(userId), jti)
    }
    
    /** 吊销某个用户的全部 refresh token，返回实际吊销数量。 */
    fun revokeAllForUser(userId: Long): Long {
        val indexKey = userIndexKey(userId)
        val jtis = redis.opsForSet().members(indexKey) ?: emptySet()
        if (jtis.isNotEmpty()) redis.delete(jtis.map(::key))
        redis.delete(indexKey)
        return jtis.size.toLong()
    }
}
