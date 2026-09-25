package top.foxball.foxskinserver.service.oauth

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import top.foxball.foxskinserver.config.OAuthProperties
import tools.jackson.databind.ObjectMapper

/**
 * 通用 OIDC 提供商：任何暴露 `/.well-known/openid-configuration` 发现文档的身份提供商均可接入，
 * 如 Keycloak、Authentik、Logto、Authelia、Casdoor 等。
 *
 * 拿到 access_token 后读取 userinfo 端点获取 `sub` 作为用户标识。
 * 这里不复核 id_token 签名：令牌直接来自提供商 token 端点（全程 TLS），跳过 JWKS 轮换维护，
 * 也规避了各提供商对 id_token 签名算法（HS256/RS256/EdDSA）的分歧；见 docs/oauth-extension.md。
 */
@Component
class OidcOAuthProvider(
    private val oauthProperties: OAuthProperties,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) : OAuthProvider {
    private val restClient = restClientBuilder.build()

    @Volatile
    private var discovered: DiscoveredEndpoints? = null

    override val id: String = "oidc"
    override val displayName: String
        get() = oauthProperties.oidc.displayName
    override val enabled: Boolean
        get() = oauthProperties.oidc.issuer.isNotBlank() && oauthProperties.oidc.clientId.isNotBlank()

    override fun authorizeUrl(redirectUri: String, state: String): String {
        val endpoints = discover()
        return UriComponentsBuilder
            .fromUriString(endpoints.authorizationEndpoint)
            .queryParam("response_type", "code")
            .queryParam("client_id", oauthProperties.oidc.clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .queryParam("scope", oauthProperties.oidc.scope)
            .build().encode().toUriString()
    }

    override fun exchange(code: String, redirectUri: String): OAuthIdentity {
        val endpoints = discover()
        val accessToken = fetchAccessToken(endpoints.tokenEndpoint, code, redirectUri)
        val claims = fetchClaims(endpoints.userinfoEndpoint, accessToken)
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

    /** 发现文档在应用生命周期内基本不变，缓存后失败时重新拉取，兼顾性能与提供商配置变更。 */
    private fun discover(): DiscoveredEndpoints {
        discovered?.let { return it }
        val issuer = oauthProperties.oidc.issuer.trimEnd('/')
        return fetchDiscovery("$issuer/.well-known/openid-configuration").also { discovered = it }
    }

    private fun fetchDiscovery(url: String): DiscoveredEndpoints {
        val fields = parseJsonFields(fetchText(url)) { throw OAuthProviderException("OIDC 发现文档不是合法 JSON") }
        val authorization = fields["authorization_endpoint"] ?: throw OAuthProviderException("OIDC 发现文档缺少 authorization_endpoint")
        val token = fields["token_endpoint"] ?: throw OAuthProviderException("OIDC 发现文档缺少 token_endpoint")
        val userinfo = fields["userinfo_endpoint"] ?: throw OAuthProviderException("OIDC 发现文档缺少 userinfo_endpoint")
        return DiscoveredEndpoints(authorization, token, userinfo)
    }

    private fun fetchAccessToken(tokenEndpoint: String, code: String, redirectUri: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", redirectUri)
            add("client_id", oauthProperties.oidc.clientId)
            add("client_secret", oauthProperties.oidc.clientSecret)
        }
        val fields = parseJsonFields(
            try {
                restClient.post().uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(String::class.java).orEmpty()
            } catch (_: RestClientException) {
                throw OAuthProviderException("OIDC 提供商授权码无效或服务暂不可用")
            },
        ) { throw OAuthProviderException("OIDC token 响应不是合法 JSON") }
        return fields["access_token"]?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("OIDC 提供商未返回访问令牌")
    }

    private fun fetchClaims(userinfoEndpoint: String, accessToken: String): Map<String, String> {
        val body = try {
            restClient.get().uri(userinfoEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(String::class.java).orEmpty()
        } catch (_: RestClientException) {
            throw OAuthProviderException("OIDC userinfo 请求失败")
        }
        return parseJsonFields(body) { throw OAuthProviderException("OIDC userinfo 响应不是合法 JSON") }
    }

    /** 发现文档等 GET 端点统一走这里；各端点对非法凭据可能返回 200 + 错误文本，由调用方按 JSON 解析后校验。 */
    private fun fetchText(url: String): String = try {
        restClient.get().uri(url)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve().body(String::class.java).orEmpty()
    } catch (_: RestClientException) {
        throw OAuthProviderException("OIDC 提供商服务暂不可用")
    }

    private fun parseJsonFields(json: String, onInvalid: () -> OAuthProviderException): Map<String, String> = try {
        @Suppress("UNCHECKED_CAST")
        val fields = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        fields.mapValues { (_, value) -> value.toString() }
    } catch (_: Exception) {
        throw onInvalid()
    }

    private data class DiscoveredEndpoints(
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val userinfoEndpoint: String,
    )
}
