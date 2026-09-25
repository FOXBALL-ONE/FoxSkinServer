package top.foxball.foxskinserver

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import top.foxball.foxskinserver.config.OAuthProperties
import top.foxball.foxskinserver.service.oauth.OAuthProviderException
import top.foxball.foxskinserver.service.oauth.OidcOAuthProvider
import tools.jackson.databind.json.JsonMapper

/** 通用 OIDC 提供商：锁定发现文档解析、授权码流程与声明到身份的映射。 */
class OidcOAuthProviderTest {
    private val objectMapper = JsonMapper.builder().build()
    private val issuer = "https://sso.example.com/realms/foxskin"

    private fun provider(builder: RestClient.Builder = RestClient.builder(), issuerUrl: String = issuer) =
        OidcOAuthProvider(
            OAuthProperties().apply {
                oidc.issuer = issuerUrl
                oidc.clientId = "foxskin-web"
                oidc.clientSecret = "oidc-secret"
            },
            builder,
            objectMapper,
        )

    private fun stubDiscovery(server: MockRestServiceServer) {
        server.expect(once(), requestTo("$issuer/.well-known/openid-configuration"))
            .andRespond(
                withSuccess(
                    """{"authorization_endpoint":"$issuer/protocol/openid-connect/auth",
                        "token_endpoint":"$issuer/protocol/openid-connect/token",
                        "userinfo_endpoint":"$issuer/protocol/openid-connect/userinfo"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
    }

    @Test
    fun `disabled until issuer and client id configured`() {
        val provider = OidcOAuthProvider(OAuthProperties(), RestClient.builder(), objectMapper)
        assertFalse(provider.enabled)
        assertTrue(provider().enabled)
    }

    @Test
    fun `authorize url built from discovered authorization endpoint`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        stubDiscovery(server)

        val url = provider(builder).authorizeUrl("https://skin.example.com/api/auth/oauth/oidc/callback", "state-1")

        assertTrue(url.startsWith("$issuer/protocol/openid-connect/auth?"))
        assertTrue(url.contains("client_id=foxskin-web"))
        assertTrue(url.contains("scope=openid%20profile%20email"))
        server.verify()
    }

    @Test
    fun `exchange posts code to token endpoint and reads userinfo`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        stubDiscovery(server)
        server.expect(once(), requestTo("$issuer/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("grant_type=authorization_code")))
            .andExpect(content().string(containsString("code=the-code")))
            .andRespond(withSuccess("""{"access_token":"AT","token_type":"Bearer"}""", MediaType.APPLICATION_JSON))
        server.expect(once(), requestTo("$issuer/protocol/openid-connect/userinfo"))
            .andExpect(header("Authorization", "Bearer AT"))
            .andRespond(
                withSuccess(
                    """{"sub":"sub-77","preferred_username":"alice","nickname":"Ali","picture":"http://p/1.png","email":"alice@oidc.dev"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val identity = provider(builder).exchange("the-code", "https://skin.example.com/api/auth/oauth/oidc/callback")

        assertEquals("sub-77", identity.openId)
        assertEquals("Ali", identity.nickname)
        assertEquals("http://p/1.png", identity.avatarUrl)
        assertEquals("alice@oidc.dev", identity.email)
        server.verify()
    }

    @Test
    fun `broken discovery document surfaces as provider exception`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), requestTo("$issuer/.well-known/openid-configuration"))
            .andRespond(withSuccess("""{"token_endpoint":"$issuer/token"}""", MediaType.APPLICATION_JSON))

        assertThrows(OAuthProviderException::class.java) {
            provider(builder).authorizeUrl("https://skin.example.com/api/auth/oauth/oidc/callback", "s")
        }
        server.verify()
    }
}
