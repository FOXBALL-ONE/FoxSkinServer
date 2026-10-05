package top.foxball.foxskinserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import top.foxball.foxskinserver.config.TotpProperties
import top.foxball.foxskinserver.repository.UserTotpBackupCodeRepository
import top.foxball.foxskinserver.repository.UserTotpRepository
import top.foxball.foxskinserver.service.TotpCryptoService
import top.foxball.foxskinserver.service.TotpService
import top.foxball.foxskinserver.security.TotpLoginRateLimiter
import org.springframework.data.redis.core.StringRedisTemplate

class TotpCryptoServiceTest {
    private val service = TotpCryptoService(
        TotpProperties(
            encryptionKey = "a".repeat(32),
            backupPepper = "backup-pepper-for-tests",
        ),
    )

    @Test
    fun `encrypted secret round trips without exposing plaintext`() {
        val encrypted = service.encrypt("JBSWY3DPEHPK3PXP")

        assertTrue(encrypted.startsWith("v1."))
        assertNotEquals("JBSWY3DPEHPK3PXP", encrypted)
        assertEquals("JBSWY3DPEHPK3PXP", service.decrypt(encrypted))
    }

    @Test
    fun `backup code digest is deterministic and formatted code is random`() {
        val code = service.randomBackupCode()

        assertTrue(code.matches(Regex("\\d{5}-\\d{5}")))
        assertEquals(service.digestBackupCode(code.replace("-", "")), service.digestBackupCode(code.replace("-", "")))
    }

    @Test
    fun `totp generator matches rfc 6238 vector`() {
        val totp = TotpService(
            mock(UserTotpRepository::class.java),
            mock(UserTotpBackupCodeRepository::class.java),
            service,
            TotpProperties(
                encryptionKey = "a".repeat(32),
                backupPepper = "backup-pepper-for-tests",
                digits = 8,
            ),
            mock(StringRedisTemplate::class.java),
            mock(TotpLoginRateLimiter::class.java),
        )
        val method = TotpService::class.java.getDeclaredMethod("generateCode", String::class.java, Long::class.javaPrimitiveType)
        method.isAccessible = true

        assertEquals("94287082", method.invoke(totp, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1L))

        val decode = TotpService::class.java.getDeclaredMethod("decodeBase32", String::class.java)
        decode.isAccessible = true
        assertEquals(20, (decode.invoke(totp, service.generateSecret()) as ByteArray).size)
    }
}
