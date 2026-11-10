package top.foxball.foxskinserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import top.foxball.foxskinserver.config.OAuthProperties
import top.foxball.foxskinserver.service.oauth.SsoOAuthProvider
import tools.jackson.databind.json.JsonMapper

/** 企业 SSO 提供商：复用 OIDC 协议，额外把 preferred_username / email_verified 映射进身份。 */
class SsoOAuthProviderTest {
    private val objectMapper = JsonMapper.builder().build()
    private val issuer = "https://sso.example.com/realms/foxskin"

    private fun provider(builder: RestClient.Builder = RestClient.builder()) =
        SsoOAuthProvider(
            OAuthProperties().apply {
                sso.issuer = issuer
                sso.clientId = "foxskin-sso"
                sso.clientSecret = "sso-secret"
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
        assertFalse(SsoOAuthProvider(OAuthProperties(), RestClient.builder(), objectMapper).enabled)
        assertTrue(provider().enabled)
        assertEquals("sso", provider().id)
    }

    @Test
    fun `exchange maps preferred_username and verified email into identity`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        stubDiscovery(server)
        server.expect(once(), requestTo("$issuer/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"access_token":"AT"}""", MediaType.APPLICATION_JSON))
        server.expect(once(), requestTo("$issuer/protocol/openid-connect/userinfo"))
            .andExpect(header("Authorization", "Bearer AT"))
            .andRespond(
                withSuccess(
                    """{"sub":"sub-42","preferred_username":"alice","name":"Alice Doe",
                        "email":"alice@corp.dev","email_verified":true}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val identity = provider(builder).exchange("code", "https://skin.example.com/api/auth/oauth/sso/callback")

        assertEquals("sub-42", identity.openId)
        assertEquals("alice", identity.username)
        assertEquals("Alice Doe", identity.nickname)
        assertEquals("alice@corp.dev", identity.email)
        assertTrue(identity.emailVerified)
        server.verify()
    }

    @Test
    fun `exchange keeps emailVerified false when provider reports unverified`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        stubDiscovery(server)
        server.expect(once(), requestTo("$issuer/protocol/openid-connect/token"))
            .andRespond(withSuccess("""{"access_token":"AT"}""", MediaType.APPLICATION_JSON))
        server.expect(once(), requestTo("$issuer/protocol/openid-connect/userinfo"))
            .andRespond(
                withSuccess(
                    """{"sub":"sub-9","preferred_username":"bob","email":"bob@corp.dev","email_verified":false}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val identity = provider(builder).exchange("code", "https://skin.example.com/api/auth/oauth/sso/callback")

        assertEquals("bob", identity.username)
        assertFalse(identity.emailVerified)
        server.verify()
    }
}
