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
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import top.foxball.foxskinserver.config.OAuthProperties
import top.foxball.foxskinserver.service.oauth.OAuthProviderException
import top.foxball.foxskinserver.service.oauth.QQOAuthProvider
import tools.jackson.databind.json.JsonMapper

/** QQ 互联提供商：锁定授权页拼接、三段式换取与 JSON/键值对/JSONP 兼容解析。 */
class QQOAuthProviderTest {
    private val objectMapper = JsonMapper.builder().build()

    private fun provider(properties: OAuthProperties = defaultProperties(), builder: RestClient.Builder = RestClient.builder()) =
        QQOAuthProvider(properties, builder, objectMapper)

    private fun defaultProperties() = OAuthProperties().apply {
        qq.clientId = "app-100"
        qq.clientSecret = "secret-x"
    }

    @Test
    fun `authorize url carries client id redirect uri state and scope`() {
        val url = provider().authorizeUrl("https://skin.example.com/api/auth/oauth/qq/callback", "state-1")

        assertTrue(url.startsWith("https://graph.qq.com/oauth2.0/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=app-100"))
        assertTrue(url.contains("state=state-1"))
        assertTrue(url.contains("scope=get_user_info"), url)
        assertTrue(url.contains("redirect_uri="), url)
    }

    @Test
    fun `disabled until credentials configured`() {
        assertFalse(provider(OAuthProperties()).enabled)
        assertTrue(provider().enabled)
    }

    @Test
    fun `exchange walks token openid and userinfo`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), method(HttpMethod.GET))
            .andExpect(queryParam("code", "abc"))
            .andExpect(queryParam("grant_type", "authorization_code"))
            .andRespond(withSuccess("""{"access_token":"TOKEN","expires_in":7776000}""", MediaType.APPLICATION_JSON))
        server.expect(once(), method(HttpMethod.GET))
            .andExpect(queryParam("access_token", "TOKEN"))
            .andRespond(withSuccess("""{"client_id":"app-100","openid":"OPEN-1","unionid":"UNION-1"}""", MediaType.APPLICATION_JSON))
        server.expect(once(), method(HttpMethod.GET))
            .andExpect(queryParam("openid", "OPEN-1"))
            .andExpect(queryParam("oauth_consumer_key", "app-100"))
            .andRespond(withSuccess("""{"ret":0,"nickname":"Steve","figureurl_qq_2":"http://q.qqq/100.png"}""", MediaType.APPLICATION_JSON))

        val identity = provider(builder = builder).exchange("abc", "https://skin.example.com/api/auth/oauth/qq/callback")

        assertEquals("OPEN-1", identity.openId)
        assertEquals("UNION-1", identity.unionId)
        assertEquals("Steve", identity.nickname)
        assertEquals("http://q.qqq/100.png", identity.avatarUrl)
        server.verify()
    }

    @Test
    fun `exchange tolerates legacy key-value token response and jsonp openid`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), method(HttpMethod.GET))
            .andRespond(withSuccess("access_token=TOKEN&expires_in=7776000", MediaType.TEXT_PLAIN))
        server.expect(once(), method(HttpMethod.GET))
            .andRespond(withSuccess("""callback( {"client_id":"app-100","openid":"OPEN-2"} );""", MediaType.TEXT_PLAIN))
        server.expect(once(), method(HttpMethod.GET))
            .andRespond(withSuccess("""{"ret":0,"nickname":"Alex"}""", MediaType.APPLICATION_JSON))

        val identity = provider(builder = builder).exchange("abc", "https://skin.example.com/api/auth/oauth/qq/callback")

        assertEquals("OPEN-2", identity.openId)
        assertEquals("Alex", identity.nickname)
        server.verify()
    }

    @Test
    fun `exchange fails on provider error payload`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), method(HttpMethod.GET))
            .andRespond(withSuccess("access_token=", MediaType.APPLICATION_JSON))

        assertThrows(OAuthProviderException::class.java) {
            provider(builder = builder).exchange("bad", "https://skin.example.com/api/auth/oauth/qq/callback")
        }
        server.verify()
    }
}
