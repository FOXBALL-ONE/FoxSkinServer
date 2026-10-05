package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.config.TotpProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class TotpCryptoService(private val properties: TotpProperties) {
    private val random = SecureRandom()

    fun generateSecret(): String {
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        return Base32.getEncoder().withoutPadding().encodeToString(bytes)
    }

    fun encrypt(value: String): String {
        val key = key(properties.encryptionKey)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + encrypted)
    }

    fun decrypt(value: String): String {
        require(value.startsWith("v1.")) { "不支持的 TOTP 密钥版本" }
        val bytes = Base64.getUrlDecoder().decode(value.removePrefix("v1."))
        require(bytes.size > 12) { "TOTP 密钥密文无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(properties.encryptionKey), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(StandardCharsets.UTF_8)
    }

    fun digestBackupCode(normalizedCode: String): String {
        require(properties.backupPepper.isNotBlank()) { "TOTP_BACKUP_PEPPER 未配置" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.backupPepper.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(normalizedCode.toByteArray(StandardCharsets.US_ASCII)).joinToString("") { "%02x".format(it) }
    }

    fun secureEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.US_ASCII), right.toByteArray(StandardCharsets.US_ASCII)
    )

    fun randomBackupCode(): String {
        val raw = random.nextLong(10_000_000_000L).toString().padStart(10, '0')
        return "${raw.substring(0, 5)}-${raw.substring(5)}"
    }

    private fun key(raw: String): SecretKeySpec {
        require(raw.isNotBlank()) { "TOTP_ENCRYPTION_KEY 未配置" }
        val decoded = runCatching { Base64.getDecoder().decode(raw) }.getOrNull()
        val bytes = if (decoded != null && decoded.size >= 32) decoded else raw.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= 32) { "TOTP_ENCRYPTION_KEY 必须至少为 256 位" }
        return SecretKeySpec(bytes.copyOf(32), "AES")
    }

    private object Base32 {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        fun getEncoder() = Encoder()
        class Encoder {
            fun withoutPadding(): Encoder = this
            fun encodeToString(input: ByteArray): String {
                val out = StringBuilder((input.size * 8 + 4) / 5)
                var buffer = 0L
                var bits = 0
                for (byte in input) {
                    buffer = (buffer shl 8) or (byte.toLong() and 0xff)
                    bits += 8
                    while (bits >= 5) {
                        bits -= 5
                        out.append(ALPHABET[((buffer shr bits) and 31L).toInt()])
                        buffer = if (bits == 0) 0 else buffer and ((1L shl bits) - 1)
                    }
                }
                if (bits > 0) out.append(ALPHABET[((buffer shl (5 - bits)) and 31L).toInt()])
                return out.toString()
            }
        }
    }
}
