package top.foxball.foxskinserver.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import top.foxball.foxskinserver.config.OAuthProperties
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

/**
 * OAuth 授权流程的 state 凭据存储。
 *
 * 发起授权时写入一条一次性 state（携带流程模式与回跳路径），提供商回调时消费：
 * 不存在、已过期或不匹配都视为非法回调，防 CSRF 与回调伪造。存取走 Redis，多实例部署天然共享。
 */
@Repository
class OAuthStateStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val oauthProperties: OAuthProperties,
) {
    /** 回调完成后的动作：登录或绑定当前已登录用户。 */
    enum class Mode { LOGIN, BIND }

    data class StatePayload(
        val provider: String,
        val mode: Mode,
        /** 登录成功后前端要去的相对路径；绑定场景不需要。 */
        val redirect: String = "",
        /** BIND 模式下待绑定的皮肤站用户主键。 */
        val userId: Long = 0,
    )

    /** 生成并登记一条 state，返回浏览器要携带的原样字符串。 */
    fun issue(payload: StatePayload): String {
        val state = UUID.randomUUID().toString().replace("-", "")
        redis.opsForValue()
            .set(key(state), objectMapper.writeValueAsString(payload), Duration.ofSeconds(oauthProperties.stateTtlSeconds))
        return state
    }

    /** 消费一条 state；不存在、过期或 provider 不匹配都返回 null。 */
    fun consume(state: String, provider: String): StatePayload? {
        val raw = redis.opsForValue().getAndDelete(key(state)) ?: return null
        val payload = try {
            objectMapper.readValue(raw, StatePayload::class.java)
        } catch (_: Exception) {
            return null
        }
        if (payload.provider != provider) return null
        return payload
    }

    private fun key(state: String) = "oauth:state:$state"
}
