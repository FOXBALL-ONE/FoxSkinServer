package top.foxball.foxskinserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import top.foxball.foxskinserver.config.FileProperties
import top.foxball.foxskinserver.config.YggdrasilProperties
import top.foxball.foxskinserver.entity.jdbc.Player
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.repository.PlayerRepository
import top.foxball.foxskinserver.repository.TextureRepository
import top.foxball.foxskinserver.repository.UserRepository
import top.foxball.foxskinserver.security.YggdrasilTokenStore
import top.foxball.foxskinserver.service.YggdrasilService
import top.foxball.foxskinserver.handler.YggdrasilException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Base64
import java.util.UUID
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** 对照 Blessing Skin 上游协议，锁定令牌、Profile 和请求用户字段的关键行为。 */
class YggdrasilServiceTest {
    @Test
    fun `metadata exposes pem public key`() {
        val service = service()

        val key = service.metadata()["signaturePublickey"] as String

        assertTrue(key.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(key.contains("-----END PUBLIC KEY-----"))
    }

    @Test
    fun `signed profile puts signature required in textures payload`() {
        val players = mock(PlayerRepository::class.java)
        val player = Player(
            id = 1,
            userId = 7,
            uuid = UUID.fromString("12345678-1234-4234-8234-123456789abc"),
            name = "Steve",
        )
        `when`(players.findByUuid(player.uuid)).thenReturn(player)
        val service = service(players = players)

        val profile = service.profile(player.uuid.toString(), unsigned = false)!!
        val property = (profile["properties"] as List<*>).single() as Map<*, *>
        val payload = String(Base64.getDecoder().decode(property["value"] as String))

        assertTrue(payload.contains("\"signatureRequired\":true"))
        assertTrue(payload.contains("\"isPublic\":true"))
        assertNotNull(property["signature"])
        assertFalse(profile.containsKey("signatureRequired"))

        val publicKeyPem = service.metadata()["signaturePublickey"] as String
        val publicKeyDer = Base64.getMimeDecoder().decode(
            publicKeyPem.replace(Regex("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s"), ""),
        )
        val verifier = Signature.getInstance("SHA1withRSA")
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer)))
        verifier.update((property["value"] as String).toByteArray(StandardCharsets.UTF_8))
        assertTrue(verifier.verify(Base64.getDecoder().decode(property["signature"] as String)))
    }

    @Test
    fun `authenticate supports request user and normalizes email`() {
        val users = mock(UserRepository::class.java)
        val passwordEncoder = mock(PasswordEncoder::class.java)
        val user = User(id = 7, email = "user@example.com", password = "encoded", username = "user", nickname = "User")
        `when`(users.findByEmail("user@example.com")).thenReturn(user)
        `when`(passwordEncoder.matches("secret", "encoded")).thenReturn(true)
        val service = service(users = users, passwordEncoder = passwordEncoder)

        val result = service.authenticate(" USER@EXAMPLE.COM ", "secret", null, null, requestUser = true)

        val userData = requireNotNull(result.user)
        assertEquals(32, (userData["id"] as String).length)
        assertEquals(emptyList<Any>(), userData["properties"])
    }

    @Test
    fun `invalidate is idempotent for unknown token`() {
        val tokenStore = mock(YggdrasilTokenStore::class.java)
        `when`(tokenStore.find("missing")).thenReturn(null)
        val service = service(tokenStore = tokenStore)

        service.invalidate("missing", "client")

        verify(tokenStore, never()).revoke("missing")
    }

    @Test
    fun `profile name search returns only id and name summaries`() {
        val players = mock(PlayerRepository::class.java)
        val player = Player(
            id = 1,
            userId = 7,
            uuid = UUID.fromString("12345678-1234-4234-8234-123456789abc"),
            name = "Steve",
        )
        `when`(players.findByName("Steve")).thenReturn(player)
        val service = service(players = players)

        val profiles = service.profiles(listOf("Steve", "Steve"))

        assertEquals(listOf(mapOf("id" to "12345678123442348234123456789abc", "name" to "Steve")), profiles)
    }

    @Test
    fun `profile name search rejects more than configured limit`() {
        val service = service()

        val exception = assertThrows(YggdrasilException::class.java) {
            service.profiles((1..6).map { "Player$it" })
        }

        assertEquals("ForbiddenOperationException", exception.error)
    }

    @Test
    fun `profile payload marks only alex and slim skins as slim model`() {
        val players = mock(PlayerRepository::class.java)
        val player = Player(
            id = 1,
            userId = 7,
            uuid = UUID.fromString("12345678-1234-4234-8234-123456789abc"),
            name = "Alex",
            skinTextureId = 9,
        )
        val texture = top.foxball.foxskinserver.entity.jdbc.Texture(
            id = 9,
            type = "steve",
            hash = "a".repeat(64),
        )
        `when`(players.findByUuid(player.uuid)).thenReturn(player)
        val textures = mock(TextureRepository::class.java)
        `when`(textures.findByIdWithFile(9)).thenReturn(texture)
        val service = service(players = players, textures = textures)

        val profile = service.profile(player.uuid.toString(), unsigned = true)!!
        val property = (profile["properties"] as List<*>).single() as Map<*, *>
        val payload = String(Base64.getDecoder().decode(property["value"] as String))

        assertFalse(payload.contains("\"metadata\""))
    }

    @Test
    fun `metadata normalizes configured texture hosts`() {
        val service = service(
            properties = YggdrasilProperties(
                skinDomains = listOf("https://skin.example.com:443/assets", "skin.example.com"),
                textureBaseUrl = "https://textures.example.com:8443",
            ),
        )

        val domains = service.metadata()["skinDomains"] as List<*>

        assertTrue(domains.contains("skin.example.com"))
        assertTrue(domains.contains("textures.example.com"))
        assertFalse(domains.any { it.toString().contains("://") })
    }

    @Test
    fun `api path rejects trailing slash`() {
        assertThrows(IllegalArgumentException::class.java) {
            service(properties = YggdrasilProperties(apiPath = "/api/yggdrasil/"))
        }
    }

    private fun service(
        users: UserRepository = mock(UserRepository::class.java),
        players: PlayerRepository = mock(PlayerRepository::class.java),
        textures: TextureRepository = mock(TextureRepository::class.java),
        tokenStore: YggdrasilTokenStore = mock(YggdrasilTokenStore::class.java),
        passwordEncoder: PasswordEncoder = mock(PasswordEncoder::class.java),
        objectMapper: ObjectMapper = JsonMapper.builder().build(),
        properties: YggdrasilProperties = YggdrasilProperties(),
        fileProperties: FileProperties = FileProperties(),
    ) = YggdrasilService(
        users = users,
        players = players,
        textures = textures,
        tokenStore = tokenStore,
        passwordEncoder = passwordEncoder,
        properties = properties,
        fileProperties = fileProperties,
        objectMapper = objectMapper,
    )
}
