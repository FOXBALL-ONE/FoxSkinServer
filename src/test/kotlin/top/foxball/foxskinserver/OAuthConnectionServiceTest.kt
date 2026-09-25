package top.foxball.foxskinserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.entity.jdbc.UserConnection
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.UserAlreadyExistsException
import top.foxball.foxskinserver.repository.UserConnectionRepository
import top.foxball.foxskinserver.repository.UserRepository
import top.foxball.foxskinserver.service.oauth.OAuthConnectionService
import top.foxball.foxskinserver.service.oauth.OAuthIdentity

/** 扩展组件核心行为：首登建号、绑定冲突与解绑保护。 */
class OAuthConnectionServiceTest {
    private val userRepository = mock(UserRepository::class.java)
    private val connectionRepository = mock(UserConnectionRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val service = OAuthConnectionService(userRepository, connectionRepository, passwordEncoder)

    @Test
    fun `first login creates placeholder user and connection`() {
        `when`(connectionRepository.findByProviderAndOpenId("qq", "openid-1")).thenReturn(null)
        `when`(userRepository.findByUsername(anyString())).thenReturn(null)
        `when`(userRepository.findByEmail(anyString())).thenReturn(null)
        `when`(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash")
        `when`(userRepository.save(any(User::class.java))).thenAnswer { invocation ->
            val user = invocation.getArgument<User>(0)
            user.id = 42L
            user
        }
        `when`(connectionRepository.save(any(UserConnection::class.java))).thenAnswer { it.getArgument<UserConnection>(0) }

        val user = service.findOrCreateUser("qq", OAuthIdentity(openId = "openid-1", nickname = "Steve", avatarUrl = "http://a/1.png"))

        assertEquals("Steve", user.nickname)
        assertTrue(user.email.endsWith(OAuthConnectionService.NOREPLY_EMAIL_SUFFIX))
        assertEquals("bcrypt-hash", user.password)
        val captor: ArgumentCaptor<UserConnection> = ArgumentCaptor.forClass(UserConnection::class.java)
        verify(connectionRepository).save(captor.capture())
        assertEquals(42L, captor.value.userId)
        assertEquals("qq", captor.value.provider)
        assertEquals("openid-1", captor.value.openId)
    }

    @Test
    fun `known openid returns bound user without creating`() {
        val connection = UserConnection(userId = 7, provider = "qq", openId = "openid-1", nickname = "old")
        val user = User(id = 7, email = "a@b.c", password = "x", username = "alice", nickname = "Alice")
        `when`(connectionRepository.findByProviderAndOpenId("qq", "openid-1")).thenReturn(connection)
        `when`(userRepository.findUserById(7L)).thenReturn(user)

        val found = service.findOrCreateUser("qq", OAuthIdentity(openId = "openid-1"))

        assertEquals("alice", found.username)
        verify(userRepository, never()).save(any(User::class.java))
    }

    @Test
    fun `bind rejects openid owned by another user`() {
        val connection = UserConnection(userId = 9, provider = "oidc", openId = "sub-1", nickname = "")
        `when`(connectionRepository.findByProviderAndOpenId("oidc", "sub-1")).thenReturn(connection)

        assertThrows(UserAlreadyExistsException::class.java) {
            service.bind(7L, "oidc", OAuthIdentity(openId = "sub-1"))
        }
    }

    @Test
    fun `bind rejects provider already bound by current user`() {
        `when`(connectionRepository.findByProviderAndOpenId("oidc", "sub-1")).thenReturn(null)
        `when`(connectionRepository.findByUserIdAndProvider(7L, "oidc")).thenReturn(
            UserConnection(userId = 7, provider = "oidc", openId = "sub-old", nickname = ""),
        )

        assertThrows(ParamErrorException::class.java) {
            service.bind(7L, "oidc", OAuthIdentity(openId = "sub-1"))
        }
    }

    @Test
    fun `unbind blocked when placeholder user loses last credential`() {
        val user = User(id = 7, email = "qq_x${OAuthConnectionService.NOREPLY_EMAIL_SUFFIX}", password = "x", username = "qquser")
        `when`(userRepository.findUserById(7L)).thenReturn(user)
        val connection = UserConnection(userId = 7, provider = "qq", openId = "openid-1", nickname = "")
        `when`(connectionRepository.findByUserIdAndProvider(7L, "qq")).thenReturn(connection)
        `when`(connectionRepository.findAllByUserId(7L)).thenReturn(listOf(connection))

        assertThrows(ParamErrorException::class.java) { service.unbind(7L, "qq") }
    }

    @Test
    fun `unbind allowed for normal user with password fallback`() {
        val user = User(id = 7, email = "a@b.c", password = "bcrypt", username = "alice")
        val connection = UserConnection(userId = 7, provider = "qq", openId = "openid-1", nickname = "")
        `when`(userRepository.findUserById(7L)).thenReturn(user)
        `when`(connectionRepository.findByUserIdAndProvider(7L, "qq")).thenReturn(connection)
        `when`(connectionRepository.findAllByUserId(7L)).thenReturn(listOf(connection))

        service.unbind(7L, "qq")

        verify(connectionRepository).delete(connection)
    }

    @Test
    fun `oidc email falls back to placeholder when already taken`() {
        `when`(connectionRepository.findByProviderAndOpenId("oidc", "sub-9")).thenReturn(null)
        `when`(userRepository.findByUsername(anyString())).thenReturn(null)
        `when`(userRepository.findByEmail("taken@oidc.dev")).thenReturn(User(id = 9, email = "taken@oidc.dev"))
        `when`(passwordEncoder.encode(anyString())).thenReturn("hash")
        `when`(userRepository.save(any(User::class.java))).thenAnswer { invocation ->
            val user = invocation.getArgument<User>(0)
            user.id = 51L
            user
        }
        `when`(connectionRepository.save(any(UserConnection::class.java))).thenAnswer { it.getArgument<UserConnection>(0) }

        val user = service.findOrCreateUser("oidc", OAuthIdentity(openId = "sub-9", email = "taken@oidc.dev"))

        assertTrue(user.email.endsWith(OAuthConnectionService.NOREPLY_EMAIL_SUFFIX))
        assertNotNull(user.id)
    }
}
