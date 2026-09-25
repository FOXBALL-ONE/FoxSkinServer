package top.foxball.foxskinserver.service.oauth

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import top.foxball.foxskinserver.config.OAuthProperties
import tools.jackson.databind.ObjectMapper

/**
 * QQ 互联（connect.qq.com）OAuth2.0 提供商。
 *
 * 流程：authorize → GET token（`fmt=json`）→ GET me 换 OpenID（响应可能带 JSONP 包装）→
 * GET user/get_user_info 拉昵称头像。授权码交换全程走 GET 查询参数，遵循 QQ 互联文档约定。
 */
@Component
class QQOAuthProvider(
    private val oauthProperties: OAuthProperties,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) : OAuthProvider {
    private val restClient = restClientBuilder.build()

    override val id: String = "qq"
    override val displayName: String
        get() = oauthProperties.qq.displayName
    override val enabled: Boolean
        get() = oauthProperties.qq.clientId.isNotBlank() && oauthProperties.qq.clientSecret.isNotBlank()

    override fun authorizeUrl(redirectUri: String, state: String): String = UriComponentsBuilder
        .fromUriString(AUTHORIZE_URL)
        .queryParam("response_type", "code")
        .queryParam("client_id", oauthProperties.qq.clientId)
        .queryParam("redirect_uri", redirectUri)
        .queryParam("state", state)
        .queryParam("scope", oauthProperties.qq.scope)
        .build().encode().toUriString()

    override fun exchange(code: String, redirectUri: String): OAuthIdentity {
        val accessToken = fetchAccessToken(code, redirectUri)
        val me = fetchOpenId(accessToken)
        val profile = fetchUserInfo(accessToken, me.openId)
        return OAuthIdentity(
            openId = me.openId,
            unionId = me.unionId,
            nickname = profile.nickname.orEmpty(),
            avatarUrl = profile.figureurl100 ?: profile.figureurl40,
        )
    }

    private fun fetchAccessToken(code: String, redirectUri: String): String {
        val body = fetchText(
            UriComponentsBuilder
                .fromUriString(TOKEN_URL)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", oauthProperties.qq.clientId)
                .queryParam("client_secret", oauthProperties.qq.clientSecret)
                .queryParam("code", code)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("fmt", "json")
                .build().encode().toUriString(),
        )
        val fields = parseLooseJson(body)
        return fields["access_token"]?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("QQ 授权码无效")
    }

    private fun fetchOpenId(accessToken: String): OpenIdPayload {
        val body = fetchText(
            UriComponentsBuilder
                .fromUriString(ME_URL)
                .queryParam("access_token", accessToken)
                .queryParam("unionid", "1")
                .queryParam("fmt", "json")
                .build().encode().toUriString(),
        )
        val fields = parseLooseJson(body)
        val openId = fields["openid"]?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("QQ 授权已失效")
        return OpenIdPayload(openId, fields["unionid"]?.takeIf { it.isNotBlank() })
    }

    private fun fetchUserInfo(accessToken: String, openId: String): ProfilePayload {
        val body = fetchText(
            UriComponentsBuilder
                .fromUriString(USER_INFO_URL)
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", oauthProperties.qq.clientId)
                .queryParam("openid", openId)
                .build().encode().toUriString(),
        )
        val fields = parseLooseJson(body)
        fields["ret"]?.toIntOrNull()?.takeIf { it != 0 }?.let {
            throw OAuthProviderException("获取 QQ 用户信息失败（ret=$it）")
        }
        return ProfilePayload(fields["nickname"], fields["figureurl_qq_2"] ?: fields["figureurl_qq_1"], fields["figureurl_qq_1"])
    }

    /** QQ 各端点对非法凭据可能返回 200 + 错误文本，这里统一读文本后按宽松规则解析。 */
    private fun fetchText(url: String): String = try {
        restClient.get().uri(url).accept(MediaType.APPLICATION_JSON).retrieve().body(String::class.java).orEmpty()
    } catch (exception: RestClientException) {
        throw OAuthProviderException("QQ 登录服务暂不可用")
    }

    /**
     * QQ 旧接口对同一 URL 可能返回 JSON、`access_token=..&expires_in=..` 键值对或
     * `callback( {...} );` JSONP 包装，按文本形状逐一兼容。
     */
    private fun parseLooseJson(text: String): Map<String, String> {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("{") -> parseJsonFields(trimmed)
            trimmed.contains("=") -> trimmed.split("&")
                .mapNotNull { entry ->
                    val index = entry.indexOf('=')
                    if (index <= 0) null else entry.take(index) to entry.substring(index + 1)
                }.toMap()
            else -> JSONP_WRAPPER.find(trimmed)?.let { parseJsonFields(it.groupValues[1]) } ?: emptyMap()
        }
    }

    private fun parseJsonFields(json: String): Map<String, String> = try {
        @Suppress("UNCHECKED_CAST")
        val fields = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        fields.mapValues { (_, value) ->
            when (value) {
                is Boolean -> if (value) "1" else "0"
                else -> value.toString()
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private data class OpenIdPayload(val openId: String, val unionId: String?)

    private data class ProfilePayload(val nickname: String?, val figureurl100: String?, val figureurl40: String?)

    private companion object {
        const val AUTHORIZE_URL = "https://graph.qq.com/oauth2.0/authorize"
        const val TOKEN_URL = "https://graph.qq.com/oauth2.0/token"
        const val ME_URL = "https://graph.qq.com/oauth2.0/me"
        const val USER_INFO_URL = "https://graph.qq.com/user/get_user_info"

        /** 各端点对非法凭据可能返回 200 + 错误文本；`fmt=json` 下是 JSON，旧实现可能返回键值对或 JSONP 包装。 */
        val JSONP_WRAPPER = Regex("""\(\s*(\{.*})\s*\)""")
    }
}
