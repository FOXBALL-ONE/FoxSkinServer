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

    @Test
    fun `sso links to existing user by verified email`() {
        val target = User(id = 20, email = "alice@corp.dev", password = "x", username = "alice", nickname = "Alice")
        `when`(connectionRepository.findByProviderAndOpenId("sso", "sub-sso")).thenReturn(null)
        `when`(userRepository.findByEmail("alice@corp.dev")).thenReturn(target)
        `when`(connectionRepository.findByUserIdAndProvider(20L, "sso")).thenReturn(null)
        `when`(connectionRepository.save(any(UserConnection::class.java))).thenAnswer { it.getArgument<UserConnection>(0) }

        val user = service.loginViaSso(
            OAuthIdentity(openId = "sub-sso", email = "alice@corp.dev", emailVerified = true, username = "ignored"),
            autoProvision = false,
        )

        assertEquals(20L, user.id)
        verify(userRepository, never()).save(any(User::class.java))
        val captor: ArgumentCaptor<UserConnection> = ArgumentCaptor.forClass(UserConnection::class.java)
        verify(connectionRepository).save(captor.capture())
        assertEquals(20L, captor.value.userId)
        assertEquals("sso", captor.value.provider)
        assertEquals("sub-sso", captor.value.openId)
    }

    @Test
    fun `sso ignores unverified email and links by username`() {
        val target = User(id = 21, email = "bob@corp.dev", password = "x", username = "bob", nickname = "Bob")
        `when`(connectionRepository.findByProviderAndOpenId("sso", "sub-bob")).thenReturn(null)
        `when`(userRepository.findByUsername("bob")).thenReturn(target)
        `when`(connectionRepository.findByUserIdAndProvider(21L, "sso")).thenReturn(null)
        `when`(connectionRepository.save(any(UserConnection::class.java))).thenAnswer { it.getArgument<UserConnection>(0) }

        val user = service.loginViaSso(
            OAuthIdentity(openId = "sub-bob", email = "bob@corp.dev", emailVerified = false, username = "bob"),
            autoProvision = false,
        )

        assertEquals(21L, user.id)
        verify(userRepository, never()).findByEmail(anyString())
        verify(connectionRepository).save(any(UserConnection::class.java))
    }

    @Test
    fun `sso rejects login when no account matches and auto provision off`() {
        `when`(connectionRepository.findByProviderAndOpenId("sso", "sub-x")).thenReturn(null)
        `when`(userRepository.findByEmail(anyString())).thenReturn(null)
        `when`(userRepository.findByUsername(anyString())).thenReturn(null)

        assertThrows(ParamErrorException::class.java) {
            service.loginViaSso(
                OAuthIdentity(openId = "sub-x", email = "ghost@corp.dev", emailVerified = true, username = "ghost"),
                autoProvision = false,
            )
        }
        verify(userRepository, never()).save(any(User::class.java))
    }

    @Test
    fun `sso auto provisions new user when enabled and no match`() {
        `when`(connectionRepository.findByProviderAndOpenId("sso", "sub-new")).thenReturn(null)
        `when`(userRepository.findByEmail(anyString())).thenReturn(null)
        `when`(userRepository.findByUsername(anyString())).thenReturn(null)
        `when`(passwordEncoder.encode(anyString())).thenReturn("hash")
        `when`(userRepository.save(any(User::class.java))).thenAnswer { invocation ->
            val user = invocation.getArgument<User>(0)
            user.id = 60L
            user
        }
        `when`(connectionRepository.save(any(UserConnection::class.java))).thenAnswer { it.getArgument<UserConnection>(0) }

        val user = service.loginViaSso(
            OAuthIdentity(openId = "sub-new", nickname = "Newbie", email = "new@corp.dev", emailVerified = true, username = "newbie"),
            autoProvision = true,
        )

        assertEquals(60L, user.id)
        verify(userRepository).save(any(User::class.java))
    }

    @Test
    fun `sso returns bound user without rematching`() {
        val connection = UserConnection(userId = 30, provider = "sso", openId = "sub-known", nickname = "")
        val user = User(id = 30, email = "known@corp.dev", password = "x", username = "known", nickname = "Known")
        `when`(connectionRepository.findByProviderAndOpenId("sso", "sub-known")).thenReturn(connection)
        `when`(userRepository.findUserById(30L)).thenReturn(user)

        val found = service.loginViaSso(OAuthIdentity(openId = "sub-known", username = "known"), autoProvision = false)

        assertEquals(30L, found.id)
        verify(userRepository, never()).findByEmail(anyString())
        verify(connectionRepository, never()).save(any(UserConnection::class.java))
    }

    @Test
    fun `sso rejects when matched account already bound to another sso identity`() {
        val target = User(id = 40, email = "carol@corp.dev", password = "x", username = "carol", nickname = "Carol")
        `when`(connectionRepository.findByProviderAndOpenId("sso", "sub-carol-2")).thenReturn(null)
        `when`(userRepository.findByEmail("carol@corp.dev")).thenReturn(target)
        `when`(connectionRepository.findByUserIdAndProvider(40L, "sso")).thenReturn(
            UserConnection(userId = 40, provider = "sso", openId = "sub-carol-1", nickname = ""),
        )

        assertThrows(ParamErrorException::class.java) {
            service.loginViaSso(
                OAuthIdentity(openId = "sub-carol-2", email = "carol@corp.dev", emailVerified = true, username = "carol"),
                autoProvision = false,
            )
        }
        verify(connectionRepository, never()).save(any(UserConnection::class.java))
    }
}
