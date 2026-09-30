package top.foxball.foxskinserver.service.oauth

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.ObjectMapper
import top.foxball.foxskinserver.config.OAuthProperties
import top.foxball.foxskinserver.service.MojangAuthenticationStateService
import java.util.UUID

/** Microsoft OAuth -> Xbox Live -> XSTS -> Minecraft Services 的授权码实现。 */
@Component
class MicrosoftOAuthProvider(
    private val properties: OAuthProperties,
    private val mojangStateService: MojangAuthenticationStateService,
    builder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) : OAuthProvider {
    private val client = builder.build()

    override val id: String = "microsoft"
    override val displayName: String get() = properties.microsoft.displayName
    override val enabled: Boolean
        get() = mojangStateService.get().enabled && properties.microsoft.clientId.isNotBlank() && properties.microsoft.clientSecret.isNotBlank()

    override fun authorizeUrl(redirectUri: String, state: String): String = UriComponentsBuilder
        .fromUriString(AUTHORIZE_URL)
        .queryParam("client_id", properties.microsoft.clientId)
        .queryParam("response_type", "code")
        .queryParam("redirect_uri", redirectUri)
        .queryParam("scope", properties.microsoft.scope)
        .queryParam("state", state)
        .queryParam("prompt", "select_account")
        .build().encode().toUriString()

    override fun exchange(code: String, redirectUri: String): OAuthIdentity {
        val microsoftToken = postForm(TOKEN_URL, LinkedMultiValueMap<String, String>().apply {
            add("client_id", properties.microsoft.clientId)
            add("client_secret", properties.microsoft.clientSecret)
            add("code", code)
            add("grant_type", "authorization_code")
            add("redirect_uri", redirectUri)
            add("scope", properties.microsoft.scope)
        })["access_token"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("Microsoft 授权码无效或未返回访问令牌")

        val xbox = postJson(XBOX_AUTH_URL, mapOf(
            "Properties" to mapOf(
                "AuthMethod" to "RPS",
                "SiteName" to "user.auth.xboxlive.com",
                "RpsTicket" to "d=$microsoftToken",
            ),
            "RelyingParty" to "http://auth.xboxlive.com",
            "TokenType" to "JWT",
        ))
        val xboxToken = xbox["Token"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("Xbox Live 授权失败")
        val uhs = ((xbox["DisplayClaims"] as? Map<*, *>)?.get("xui") as? List<*>)
            ?.firstOrNull().let { it as? Map<*, *> }?.get("uhs")?.toString()
            ?: throw OAuthProviderException("Xbox Live 未返回用户标识")

        val xsts = postJson(XSTS_URL, mapOf(
            "Properties" to mapOf("SandboxId" to "RETAIL", "UserTokens" to listOf(xboxToken)),
            "RelyingParty" to "rp://api.minecraftservices.com/",
            "TokenType" to "JWT",
        ))
        val xstsToken = xsts["Token"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("Xbox XSTS 授权失败")

        val minecraft = postJson(MINECRAFT_LOGIN_URL, mapOf(
            "identityToken" to "XBL3.0 x=$uhs;$xstsToken",
        ))
        val minecraftToken = minecraft["access_token"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw OAuthProviderException("Minecraft Services 登录失败")
        try {
            client.get().uri(ENTITLEMENT_URL)
                .headers { it.setBearerAuth(minecraftToken) }
                .retrieve().toBodilessEntity()
        } catch (_: RestClientException) {
            throw OAuthProviderException("该微软账号没有 Minecraft Java 版授权")
        }
        val profile = try {
            val fields = objectMapper.readValue(
                client.get().uri(PROFILE_URL).headers { it.setBearerAuth(minecraftToken) }
                    .retrieve().body(String::class.java).orEmpty(), Map::class.java,
            )
            val rawId = fields["id"]?.toString()?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }
                ?: throw IllegalArgumentException()
            val name = fields["name"]?.toString()?.takeIf { it.matches(Regex("[A-Za-z0-9_]{3,16}")) }
                ?: throw IllegalArgumentException()
            UUID.fromString(rawId.replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})".toRegex(), "$1-$2-$3-$4-$5")) to name
        } catch (_: Exception) {
            throw OAuthProviderException("Minecraft Services 未返回有效玩家资料")
        }
        return OAuthIdentity(profile.first.toString(), nickname = profile.second)
    }

    private fun postForm(url: String, body: LinkedMultiValueMap<String, String>): Map<String, Any> = try {
        val raw = client.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body)
            .retrieve().body(String::class.java).orEmpty()
        @Suppress("UNCHECKED_CAST")
        objectMapper.readValue(raw, Map::class.java) as Map<String, Any>
    } catch (_: Exception) {
        throw OAuthProviderException("Microsoft 授权服务暂不可用")
    }

    private fun postJson(url: String, body: Any): Map<String, Any> = try {
        val raw = client.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
            .retrieve().body(String::class.java).orEmpty()
        @Suppress("UNCHECKED_CAST")
        objectMapper.readValue(raw, Map::class.java) as Map<String, Any>
    } catch (_: Exception) {
        throw OAuthProviderException("Microsoft 游戏授权服务暂不可用")
    }

    companion object {
        private const val AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf"
        private const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
        private const val XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val ENTITLEMENT_URL = "https://api.minecraftservices.com/entitlements/mcstore"
        private const val PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    }
}
