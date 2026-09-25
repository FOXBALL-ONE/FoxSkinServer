package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 第三方登录扩展组件配置。
 *
 * 采用"配置了凭据即启用提供商"的约定：`qq.client-id` 非空启用 QQ 互联，
 * `oidc.issuer` 与 `oidc.client-id` 都非空启用通用 OIDC，未配置任何提供商时整个组件保持静默。
 * 敏感的 client-secret 只应通过环境变量或 .env 注入。
 */
@ConfigurationProperties(prefix = "foxskin.oauth")
data class OAuthProperties(
    /** 站点对外地址（如 `https://skin.example.com`），用于构造提供商回跳的 redirect_uri；留空时从当前请求推导。 */
    var publicUrl: String = "",
    /** 前端站点地址，授权完成后浏览器最终回到的前端入口（如 `https://skin.example.com`）；留空时回退 [publicUrl]。 */
    var frontendUrl: String = "",
    /** 授权 state 的有效期（秒），防 CSRF 的一次性凭据。 */
    var stateTtlSeconds: Long = 300,
    /** QQ 互联配置。 */
    var qq: Qq = Qq(),
    /** 通用 OIDC 提供商配置。 */
    var oidc: Oidc = Oidc(),
) {
    /** QQ 互联（connect.qq.com）凭据。 */
    data class Qq(
        /** 应用的 AppID，非空时启用 QQ 登录。 */
        var clientId: String = "",
        /** 应用的 AppKey。 */
        var clientSecret: String = "",
        /** 授权 scope，QQ 互联当前仅支持 get_user_info。 */
        var scope: String = "get_user_info",
        /** 前端展示名。 */
        var displayName: String = "QQ",
    )

    /** 任意符合 OIDC Discovery 规范的身份提供商（Keycloak、Authentik、Logto、Authelia 等）。 */
    data class Oidc(
        /** 提供商的 Issuer 地址，非空时启用 OIDC 登录；Discovery 文档固定读 `{issuer}/.well-known/openid-configuration`。 */
        var issuer: String = "",
        var clientId: String = "",
        var clientSecret: String = "",
        /** 授权 scope，至少包含 openid。 */
        var scope: String = "openid profile email",
        /** 前端展示名。 */
        var displayName: String = "OIDC",
    )
}
