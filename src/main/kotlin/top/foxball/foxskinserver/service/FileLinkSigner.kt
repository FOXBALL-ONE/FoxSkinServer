package top.foxball.foxskinserver.service

import org.springframework.stereotype.Component
import top.foxball.foxskinserver.config.FileProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SignedDownloadLink(
    val scope: String,
    val expiresAt: Instant,
    val nonce: String,
    val signature: String,
)

@Component
class FileLinkSigner(private val properties: FileProperties) {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    
    fun sign(fileId: UUID, scope: String, ttlSeconds: Long): SignedDownloadLink {
        val expires = Instant.now().plusSeconds(ttlSeconds.coerceAtLeast(1))
        val nonceBytes = ByteArray(18).also(random::nextBytes)
        val nonce = encoder.encodeToString(nonceBytes)
        return SignedDownloadLink(scope, expires, nonce, signature(fileId, scope, expires.epochSecond, nonce))
    }
    
    fun isValid(fileId: UUID, scope: String, expiresAtEpochSeconds: Long, nonce: String, signature: String): Boolean {
        if (scope.isBlank() || nonce.isBlank() || signature.isBlank()) return false
        if (expiresAtEpochSeconds < Instant.now().epochSecond) return false
        val expected = signature(fileId, scope, expiresAtEpochSeconds, nonce)
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            signature.toByteArray(StandardCharsets.US_ASCII)
        )
    }
    
    private fun signature(fileId: UUID, scope: String, expires: Long, nonce: String): String {
        val payload = "$fileId\n$scope\n$expires\n$nonce".toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.signingSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return encoder.encodeToString(mac.doFinal(payload))
    }
}
