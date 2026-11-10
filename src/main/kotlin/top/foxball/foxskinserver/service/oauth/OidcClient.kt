package top.foxball.foxskinserver.service.oauth

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * OIDC Discovery + 授权码换取的协议实现，由通用 OIDC（社交登录）与企业 SSO 两个提供商复用。
 *
 * 协议细节完全一致，两者只在"落地策略"（自动建号 vs 按用户名/邮箱匹配已有账号）上分叉，
 * 而落地策略属于 [OAuthConnectionService]，不属于提供商。因此这里只封装协议，配置随调用传入，
 * 以保留"每次调用都读取最新凭据"的语义，支持运行时切换 issuer/client。
 *
 * 拿到 access_token 后读取 userinfo 端点获取声明；不复核 id_token 签名：令牌直接来自提供商
 * token 端点（全程 TLS），跳过 JWKS 轮换维护，也规避各提供商对 id_token 签名算法的分歧，
 * 见 docs/oauth-extension.md。
 */
class OidcClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) {
    /** 发现文档在应用生命周期内基本不变，按 issuer 缓存；失败时不写入，下次调用重新拉取。 */
    private val discoveryCache = ConcurrentHashMap<String, DiscoveredEndpoints>()

    fun authorizeUrl(issuer: String, clientId: String, scope: String, redirectUri: String, state: String): String {
        val endpoints = discover(issuer)
        return UriComponentsBuilder
            .fromUriString(endpoints.authorizationEndpoint)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .queryParam("scope", scope)
            .build().encode().toUriString()
    }

    /** 用授权码换取 userinfo 声明原始映射（值统一转字符串），由调用方决定取哪些声明。 */
    fun fetchClaims(
        issuer: String,
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
    ): Map<String, String> {
        val endpoints = discover(issuer)
        val accessToken = fetchAccessToken(endpoints.tokenEndpoint, clientId, clientSecret, code, redirectUri)
        return fetchUserinfo(endpoints.userinfoEndpoint, accessToken)
    }

    private fun discover(issuer: String): DiscoveredEndpoints {
        val normalized = issuer.trimEnd('/')
        discoveryCache[normalized]?.let { return it }
        return fetchDiscovery("$normalized/.well-known/openid-configuration").also { discoveryCache[normalized] = it }
    }

    private fun fetchDiscovery(url: String): DiscoveredEndpoints {
        val fields = parseJsonFields(fetchText(url)) { throw OAuthProviderException("OIDC 发现文档不是合法 JSON") }
        val authorization =
            fields["authorization_endpoint"] ?: throw OAuthProviderException("OIDC 发现文档缺少 authorization_endpoint")
        val token = fields["token_endpoint"] ?: throw OAuthProviderException("OIDC 发现文档缺少 token_endpoint")
        val userinfo =
            fields["userinfo_endpoint"] ?: throw OAuthProviderException("OIDC 发现文档缺少 userinfo_endpoint")
        return DiscoveredEndpoints(authorization, token, userinfo)
    }

    private fun fetchAccessToken(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
    ): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("redirect_uri", redirectUri)
            add("client_id", clientId)
            add("client_secret", clientSecret)
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

    private fun fetchUserinfo(userinfoEndpoint: String, accessToken: String): Map<String, String> {
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
