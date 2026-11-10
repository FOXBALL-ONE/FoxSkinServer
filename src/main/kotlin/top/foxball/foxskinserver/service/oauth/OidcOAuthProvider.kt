package top.foxball.foxskinserver.service.oauth

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import top.foxball.foxskinserver.config.OAuthProperties

/**
 * 通用 OIDC 提供商（社交登录）：任何暴露 `/.well-known/openid-configuration` 发现文档的身份提供商均可接入，
 * 如 Keycloak、Authentik、Logto、Authelia、Casdoor 等。
 *
 * 落地策略为"社交登录"：按 (provider, sub) 找不到绑定即自动建号（占位邮箱 + 随机密码）。
 * 若需要"按用户名/邮箱匹配已有账号"的企业 SSO 语义，请改用 [SsoOAuthProvider]；二者协议一致，
 * 仅落地策略不同，协议实现复用 [OidcClient]。
 */
@Component
class OidcOAuthProvider(
    private val oauthProperties: OAuthProperties,
    restClientBuilder: RestClient.Builder,
    objectMapper: ObjectMapper,
) : OAuthProvider {
    private val oidc = OidcClient(restClientBuilder.build(), objectMapper)

    override val id: String = "oidc"
    override val displayName: String
        get() = oauthProperties.oidc.displayName
    override val enabled: Boolean
        get() = oauthProperties.oidc.issuer.isNotBlank() && oauthProperties.oidc.clientId.isNotBlank()

    override fun authorizeUrl(redirectUri: String, state: String): String = oidc.authorizeUrl(
        oauthProperties.oidc.issuer,
        oauthProperties.oidc.clientId,
        oauthProperties.oidc.scope,
        redirectUri,
        state,
    )

    override fun exchange(code: String, redirectUri: String): OAuthIdentity {
        val claims = oidc.fetchClaims(
            oauthProperties.oidc.issuer,
            oauthProperties.oidc.clientId,
            oauthProperties.oidc.clientSecret,
            code,
            redirectUri,
        )
        val subject = claims["sub"]?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("OIDC 提供商未返回 sub 声明")
        return OAuthIdentity(
            openId = subject,
            // 昵称优先取友好名，preferred_username 通常是登录句柄，仅作兜底。
            nickname = claims["nickname"] ?: claims["name"] ?: claims["preferred_username"] ?: "",
            avatarUrl = claims["picture"]?.takeIf { it.isNotBlank() },
            email = claims["email"]?.takeIf { it.isNotBlank() },
        )
    }
}
