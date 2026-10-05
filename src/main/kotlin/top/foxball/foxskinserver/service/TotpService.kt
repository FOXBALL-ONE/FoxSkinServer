package top.foxball.foxskinserver.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.foxskinserver.config.TotpProperties
import top.foxball.foxskinserver.entity.jdbc.UserTotp
import top.foxball.foxskinserver.entity.jdbc.UserTotpBackupCode
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.repository.UserTotpBackupCodeRepository
import top.foxball.foxskinserver.repository.UserTotpRepository
import top.foxball.foxskinserver.security.TotpLoginRateLimiter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

data class TotpStatus(val enabled: Boolean, val confirmedAt: LocalDateTime?, val remainingBackupCodes: Long)
data class TotpSetup(val otpauthUri: String, val secret: String)
data class TotpChallenge(val challengeToken: String, val expiresIn: Long)

@Service
class TotpService(
    private val totpRepository: UserTotpRepository,
    private val backupRepository: UserTotpBackupCodeRepository,
    private val crypto: TotpCryptoService,
    private val properties: TotpProperties,
    private val redis: StringRedisTemplate,
    private val rateLimiter: TotpLoginRateLimiter,
) {
    private val random = SecureRandom()
    private val log = LoggerFactory.getLogger(TotpService::class.java)

    init {
        require(properties.periodSeconds > 0) { "TOTP 时间步必须为正数" }
        require(properties.digits in 6..8) { "TOTP 位数必须在 6-8 之间" }
        require(properties.window in 0..2) { "TOTP 时间窗口必须在 0-2 之间" }
        require(properties.challengeTtlSeconds > 0) { "TOTP 登录挑战 TTL 必须为正数" }
        require(properties.maxChallengeAttempts > 0) { "TOTP 登录挑战失败次数必须为正数" }
    }

    fun status(userId: Long): TotpStatus {
        val config = totpRepository.findByUserId(userId)
        return TotpStatus(
            enabled = config?.enabled == true,
            confirmedAt = config?.confirmedAt,
            remainingBackupCodes = backupRepository.countByUserIdAndUsedAtIsNull(userId),
        )
    }

    @Transactional
    fun setup(userId: Long, currentPasswordValid: Boolean, email: String): TotpSetup {
        if (!currentPasswordValid) throw ParamErrorException("当前密码不正确")
        val current = totpRepository.findByUserId(userId)
        if (current?.enabled == true) throw ParamErrorException("TOTP 已启用，请先停用")
        val secret = crypto.generateSecret()
        val config = current ?: UserTotp(userId = userId)
        config.pendingSecretCiphertext = crypto.encrypt(secret)
        config.updatedAt = LocalDateTime.now()
        totpRepository.save(config)
        val label = "${properties.issuer}:$email"
        val uri = "otpauth://totp/${urlEncode(label)}?secret=$secret&issuer=${urlEncode(properties.issuer)}" +
            "&algorithm=SHA1&digits=${properties.digits}&period=${properties.periodSeconds}"
        return TotpSetup(uri, secret)
    }

    @Transactional
    fun confirm(userId: Long, currentPasswordValid: Boolean, code: String): List<String> {
        if (!currentPasswordValid) throw ParamErrorException("当前密码不正确")
        val config = totpRepository.findByUserId(userId) ?: throw ParamErrorException("请先开始设置 TOTP")
        val pending = config.pendingSecretCiphertext ?: throw ParamErrorException("请先开始设置 TOTP")
        val secret = decryptSecret(pending)
        val step = verifyCode(secret, code, null) ?: throw ParamErrorException("验证码错误")
        config.secretCiphertext = pending
        config.pendingSecretCiphertext = null
        config.enabled = true
        config.confirmedAt = LocalDateTime.now()
        config.lastUsedTimeStep = step
        config.updatedAt = LocalDateTime.now()
        totpRepository.save(config)
        backupRepository.deleteByUserId(userId)
        val codes = createBackupCodes(userId)
        log.info("TOTP 已启用: user_id={}", userId)
        return codes
    }

    @Transactional
    fun disable(userId: Long, currentPasswordValid: Boolean, code: String) {
        if (!currentPasswordValid) throw ParamErrorException("当前密码不正确")
        val config = totpRepository.findByUserId(userId) ?: return
        if (config.enabled && !verifyFactor(userId, code)) throw ParamErrorException("验证码错误")
        config.enabled = false
        config.secretCiphertext = ""
        config.pendingSecretCiphertext = null
        config.lastUsedTimeStep = null
        config.updatedAt = LocalDateTime.now()
        totpRepository.save(config)
        backupRepository.deleteByUserId(userId)
        invalidateChallenges(userId)
        log.info("TOTP 已停用: user_id={}", userId)
    }

    @Transactional
    fun regenerateBackupCodes(userId: Long, currentPasswordValid: Boolean, code: String): List<String> {
        if (!currentPasswordValid) throw ParamErrorException("当前密码不正确")
        val config = totpRepository.findByUserId(userId)
            ?: throw ParamErrorException("TOTP 未启用")
        if (!config.enabled || !verifyFactor(userId, code)) throw ParamErrorException("验证码错误")
        backupRepository.deleteByUserId(userId)
        val codes = createBackupCodes(userId)
        log.info("TOTP 备用码已重新生成: user_id={}", userId)
        return codes
    }

    fun issueChallenge(userId: Long): TotpChallenge {
        val token = ByteArray(32).also(random::nextBytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(token)
        val hash = sha256(raw)
        val ttl = Duration.ofSeconds(properties.challengeTtlSeconds)
        redis.opsForValue().set(challengeKey(hash), "$userId:0", ttl)
        redis.opsForSet().add(userChallengeKey(userId), hash)
        redis.expire(userChallengeKey(userId), ttl)
        return TotpChallenge(raw, properties.challengeTtlSeconds)
    }

    fun verifyLoginChallenge(challenge: String, code: String, clientAddress: String): Long {
        if (challenge.length < 40 || challenge.length > 128) throw ParamErrorException("验证码错误或已过期")
        val hash = sha256(challenge)
        val key = challengeKey(hash)
        val original = redis.opsForValue().get(key) ?: throw ParamErrorException("验证码错误或已过期")
        val fields = original.split(':')
        val userId = fields.firstOrNull()?.toLongOrNull() ?: throw ParamErrorException("验证码错误或已过期")
        fields.getOrNull(1)?.toIntOrNull() ?: throw ParamErrorException("验证码错误或已过期")
        if (!rateLimiter.tryAcquire(userId, clientAddress)) {
            throw top.foxball.foxskinserver.handler.VerificationCodeRateLimitException("验证码尝试过于频繁，请稍后再试")
        }
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val claimed = redis.execute(
            CLAIM_CHALLENGE,
            listOf(key),
            properties.maxChallengeAttempts.toString(),
            nonce,
        ) ?: -1L
        if (claimed == -2L || claimed <= 0L || claimed != userId) {
            throw ParamErrorException("验证码错误或已过期")
        }
        val claimedValue = redis.opsForValue().get(key) ?: throw ParamErrorException("验证码错误或已过期")
        var challengeReleased = false
        try {
            if (!verifyFactor(userId, code)) {
                releaseChallenge(key, claimedValue, nonce)
                challengeReleased = true
                log.warn("TOTP 登录验证失败: user_id={}", userId)
                throw ParamErrorException("验证码错误或已过期")
            }
            val completed = redis.execute(COMPLETE_CHALLENGE, listOf(key), claimedValue) ?: 0L
            if (completed != 1L) {
                releaseChallenge(key, claimedValue, nonce)
                challengeReleased = true
                throw ParamErrorException("验证码错误或已过期")
            }
            redis.opsForSet().remove(userChallengeKey(userId), hash)
            log.info("TOTP 登录验证成功: user_id={}", userId)
            return userId
        } catch (ex: RuntimeException) {
            if (!challengeReleased && (ex !is ParamErrorException || ex.message != "验证码错误或已过期")) {
                releaseChallenge(key, claimedValue, nonce)
            }
            throw ex
        }
    }

    fun invalidateChallenges(userId: Long) {
        val indexKey = userChallengeKey(userId)
        val hashes = redis.opsForSet().members(indexKey).orEmpty()
        if (hashes.isNotEmpty()) redis.delete(hashes.map(::challengeKey))
        redis.delete(indexKey)
    }

    private fun releaseChallenge(key: String, claimedValue: String, nonce: String) {
        val result = redis.execute(
            RELEASE_CHALLENGE,
            listOf(key),
            claimedValue,
            nonce,
            properties.maxChallengeAttempts.toString(),
        ) ?: 0L
        if (result == 2L) {
            val userId = claimedValue.substringBefore(':').toLongOrNull()
            if (userId != null) redis.opsForSet().remove(userChallengeKey(userId), sha256FromKey(key))
        }
    }

    private fun verifyFactor(userId: Long, code: String): Boolean {
        val config = totpRepository.findByUserId(userId) ?: return false
        if (!config.enabled) return false
        val normalized = code.trim().replace("-", "").replace(" ", "")
        val secret = decryptSecret(config.secretCiphertext)
        val step = verifyCode(secret, normalized, config.lastUsedTimeStep)
        if (step != null) {
            val id = config.id ?: return false
            return totpRepository.claimTimeStep(id, step, LocalDateTime.now()) == 1
        }
        if (!normalized.matches(Regex("\\d{10}"))) return false
        val digest = crypto.digestBackupCode(normalized)
        val candidate = backupRepository.findAllByUserIdAndUsedAtIsNull(userId)
            .firstOrNull { crypto.secureEquals(it.codeDigest, digest) } ?: return false
        val id = candidate.id ?: return false
        val consumed = backupRepository.consume(id, LocalDateTime.now()) == 1
        if (consumed) log.info("TOTP 备用码已消费: user_id={}", userId)
        return consumed
    }

    private fun decryptSecret(ciphertext: String): String = try {
        crypto.decrypt(ciphertext)
    } catch (_: Exception) {
        throw IllegalStateException("TOTP 密钥解密失败")
    }

    private fun verifyCode(secret: String, code: String, lastStep: Long?): Long? {
        if (!code.matches(Regex("\\d{${properties.digits}}"))) return null
        val now = System.currentTimeMillis() / 1000 / properties.periodSeconds
        for (offset in -properties.window..properties.window) {
            val step = now + offset
            if (step == lastStep) continue
            if (MessageDigest.isEqual(
                    generateCode(secret, step).toByteArray(StandardCharsets.US_ASCII),
                    code.toByteArray(StandardCharsets.US_ASCII),
                )
            ) return step
        }
        return null
    }

    private fun generateCode(secret: String, step: Long): String {
        val decoded = decodeBase32(secret)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(decoded, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulus = 10.0.pow(properties.digits).toInt()
        return (binary % modulus).toString().padStart(properties.digits, '0')
    }

    private fun decodeBase32(secret: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0L
        var bits = 0
        val decoded = ByteArray(secret.length * 5 / 8 + 1)
        var count = 0
        for (char in secret.uppercase()) {
            if (char == '=') break
            val value = alphabet.indexOf(char)
            if (value < 0) throw IllegalArgumentException("TOTP 密钥格式无效")
            buffer = (buffer shl 5) or value.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                decoded[count++] = ((buffer shr bits) and 0xffL).toByte()
                buffer = if (bits == 0) 0 else buffer and ((1L shl bits) - 1)
            }
        }
        if (count == 0) throw IllegalArgumentException("TOTP 密钥为空")
        return decoded.copyOf(count)
    }

    private fun createBackupCodes(userId: Long): List<String> {
        val result = linkedSetOf<String>()
        while (result.size < BACKUP_CODE_COUNT) result += crypto.randomBackupCode()
        val codes = result.toList()
        backupRepository.saveAll(
            codes.map {
                UserTotpBackupCode(
                    userId = userId,
                    codeDigest = crypto.digestBackupCode(it.replace("-", "")),
                )
            },
        )
        return codes
    }

    private fun challengeKey(hash: String): String = "auth:totp-challenge:$hash"
    private fun userChallengeKey(userId: Long): String = "auth:totp-challenges-user:$userId"
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun sha256FromKey(key: String): String = key.substringAfterLast(':')
    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private companion object {
        const val BACKUP_CODE_COUNT = 10
        val CLAIM_CHALLENGE = DefaultRedisScript<Long>(
            """
            local value = redis.call('get', KEYS[1])
            if not value then return -1 end
            local user, attempts, nonce = string.match(value, '^(%d+):(%d+):?(.*)$')
            if not user or not attempts then return -1 end
            if nonce ~= '' then return -2 end
            if tonumber(attempts) >= tonumber(ARGV[1]) then
                redis.call('del', KEYS[1])
                return -1
            end
            local ttl = redis.call('pttl', KEYS[1])
            if ttl < 1 then
                redis.call('del', KEYS[1])
                return -1
            end
            redis.call('set', KEYS[1], user .. ':' .. attempts .. ':' .. ARGV[2], 'PX', ttl)
            return tonumber(user)
            """.trimIndent(),
            Long::class.java,
        )
        val RELEASE_CHALLENGE = DefaultRedisScript<Long>(
            """
            if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
            local user, attempts, nonce = string.match(ARGV[1], '^(%d+):(%d+):?(.*)$')
            if nonce ~= ARGV[2] then return 0 end
            local next_attempt = tonumber(attempts) + 1
            if next_attempt >= tonumber(ARGV[3]) then
                redis.call('del', KEYS[1])
                return 2
            end
            local ttl = redis.call('pttl', KEYS[1])
            if ttl < 1 then
                redis.call('del', KEYS[1])
                return 2
            end
            redis.call('set', KEYS[1], user .. ':' .. next_attempt, 'PX', ttl)
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        val COMPLETE_CHALLENGE = DefaultRedisScript<Long>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
