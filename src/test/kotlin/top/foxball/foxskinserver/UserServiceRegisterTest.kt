package top.foxball.foxskinserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.UserAlreadyExistsException
import top.foxball.foxskinserver.repository.UserRepository
import top.foxball.foxskinserver.service.CapeService
import top.foxball.foxskinserver.service.FileService
import top.foxball.foxskinserver.service.PlayerService
import top.foxball.foxskinserver.service.ReportService
import top.foxball.foxskinserver.service.TextureService
import top.foxball.foxskinserver.service.UserClosetService
import top.foxball.foxskinserver.service.UserService

/** 开放注册的参数校验与唯一性约束。 */
class UserServiceRegisterTest {
    private val userRepository = mock(UserRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val service = UserService(
        userRepository,
        mock(PlayerService::class.java),
        mock(UserClosetService::class.java),
        mock(TextureService::class.java),
        mock(CapeService::class.java),
        mock(ReportService::class.java),
        mock(FileService::class.java),
        passwordEncoder,
    )

    @Test
    fun `register stores normalized email lowercased and unverified`() {
        `when`(userRepository.findByEmail("steve@foxskin.dev")).thenReturn(null)
        `when`(userRepository.findByUsername("steve")).thenReturn(null)
        `when`(passwordEncoder.encode("super-secret-1")).thenReturn("bcrypt")
        `when`(userRepository.save(any(User::class.java))).thenAnswer { it.getArgument<User>(0) }

        val user = service.register("  Steve@Foxskin.dev ", "steve", "super-secret-1", "  Steve!  ")

        assertEquals("steve@foxskin.dev", user.email)
        assertEquals("bcrypt", user.password)
        assertEquals("Steve!", user.nickname)
        assertFalse(user.verified)
    }

    @Test
    fun `register rejects duplicate email`() {
        `when`(userRepository.findByEmail("taken@foxskin.dev")).thenReturn(User(id = 1))
        assertThrows(UserAlreadyExistsException::class.java) {
            service.register("taken@foxskin.dev", "alice", "super-secret-1", null)
        }
    }

    @Test
    fun `register rejects duplicate username`() {
        `when`(userRepository.findByEmail("new@foxskin.dev")).thenReturn(null)
        `when`(userRepository.findByUsername("steve")).thenReturn(User(id = 1))
        assertThrows(UserAlreadyExistsException::class.java) {
            service.register("new@foxskin.dev", "steve", "super-secret-1", null)
        }
    }

    @Test
    fun `register rejects invalid username pattern`() {
        assertThrows(ParamErrorException::class.java) {
            service.register("a@foxskin.dev", "bad name!", "super-secret-1", null)
        }
    }

    @Test
    fun `register rejects short password and invalid email`() {
        assertThrows(ParamErrorException::class.java) {
            service.register("a@foxskin.dev", "alice", "short", null)
        }
        assertThrows(ParamErrorException::class.java) {
            service.register("not-an-email", "alice", "super-secret-1", null)
        }
    }
}
