package top.foxball.foxskinserver.service.oauth

import org.springframework.http.HttpStatus
import top.foxball.foxskinserver.handler.BusinessException

/** 提供商在授权码交换或拉取用户信息环节失败时抛出，由全局异常处理器统一响应。 */
class OAuthProviderException(message: String) : BusinessException(HttpStatus.BAD_REQUEST, message)

/** 提供商侧成功换取到的外部身份。 */
data class OAuthIdentity(
    /** 提供商内唯一标识：QQ OpenID、OIDC `sub`。 */
    val openId: String,
    /** 跨应用标识：QQ UnionID；OIDC 场景为空。 */
    val unionId: String? = null,
    /** 展示昵称，可为空。 */
    val nickname: String = "",
    /** 头像地址，可为空。 */
    val avatarUrl: String? = null,
    /** 提供商返回的邮箱（OIDC `email` 声明），QQ 互联不提供，可为空。 */
    val email: String? = null,
)

/**
 * 第三方登录提供商 SPI。
 *
 * 扩展一个新的登录渠道 = 实现本接口并注册为 Spring Bean：授权页地址拼接、
 * 授权码换取与身份拉取的协议细节都封闭在实现内部，登录/绑定流程只依赖 [exchange]。
 */
interface OAuthProvider {
    /** 路由与绑定关系里使用的组件 id，如 `qq`、`oidc`。 */
    val id: String

    /** 前端按钮展示名。 */
    val displayName: String

    /** 凭据是否齐备；未配置凭据的提供商不会出现在提供商列表与回调路由中。 */
    val enabled: Boolean

    /** 构造提供商授权页地址，[state] 为 [OAuthStateStore] 签发的一次性凭据。 */
    fun authorizeUrl(redirectUri: String, state: String): String

    /** 用授权码换取外部身份；协议细节（token 端点、OpenID、userinfo）由实现封闭。 */
    fun exchange(code: String, redirectUri: String): OAuthIdentity
}
