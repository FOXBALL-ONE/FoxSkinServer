package top.foxball.foxskinserver.service.oauth

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import top.foxball.foxskinserver.config.OAuthProperties

/**
 * 企业 SSO 提供商（参考 Keycloak）：与 [OidcOAuthProvider] 走同一套 OIDC 协议（复用 [OidcClient]），
 * 区别只在登录落地策略——SSO 不自动建号，而是按提供商返回的 `preferred_username` / `email` 匹配
 * 本站已有账号并自动绑定，详见 [OAuthConnectionService.loginViaSso]。
 *
 * 独立于 `oidc` 组件配置（`foxskin.oauth.sso.*`），因此同一部署可同时开启"社交 OIDC 登录"与
 * "企业 SSO 登录"两个不同的身份源。
 */
@Component
class SsoOAuthProvider(
    private val oauthProperties: OAuthProperties,
    restClientBuilder: RestClient.Builder,
    objectMapper: ObjectMapper,
) : OAuthProvider {
    private val oidc = OidcClient(restClientBuilder.build(), objectMapper)

    override val id: String = "sso"
    override val displayName: String
        get() = oauthProperties.sso.displayName
    override val enabled: Boolean
        get() = oauthProperties.sso.issuer.isNotBlank() && oauthProperties.sso.clientId.isNotBlank()

    override fun authorizeUrl(redirectUri: String, state: String): String = oidc.authorizeUrl(
        oauthProperties.sso.issuer,
        oauthProperties.sso.clientId,
        oauthProperties.sso.scope,
        redirectUri,
        state,
    )

    override fun exchange(code: String, redirectUri: String): OAuthIdentity {
        val claims = oidc.fetchClaims(
            oauthProperties.sso.issuer,
            oauthProperties.sso.clientId,
            oauthProperties.sso.clientSecret,
            code,
            redirectUri,
        )
        val subject = claims["sub"]?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("SSO 提供商未返回 sub 声明")
        return OAuthIdentity(
            openId = subject,
            nickname = claims["nickname"] ?: claims["name"] ?: claims["preferred_username"] ?: "",
            avatarUrl = claims["picture"]?.takeIf { it.isNotBlank() },
            email = claims["email"]?.takeIf { it.isNotBlank() },
            username = claims["preferred_username"]?.takeIf { it.isNotBlank() },
            // email_verified 经发现文档解析统一转成字符串，这里按文本判定。
            emailVerified = claims["email_verified"].equals("true", ignoreCase = true),
        )
    }
}
