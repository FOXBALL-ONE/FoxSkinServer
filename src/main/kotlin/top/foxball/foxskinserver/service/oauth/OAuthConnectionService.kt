package top.foxball.foxskinserver.service.oauth

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.entity.jdbc.UserConnection
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.UserAlreadyExistsException
import top.foxball.foxskinserver.repository.UserConnectionRepository
import top.foxball.foxskinserver.repository.UserRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * 第三方账号与皮肤站用户的关联服务，QQ/OIDC 等提供商共用的扩展组件核心。
 *
 * 登录场景按 (provider, openId) 找已有绑定，找不到时自动注册新用户（占位邮箱 + 随机密码）；
 * 绑定场景把外部身份挂到当前登录用户上；解绑时若该连接是账号仅剩的登录方式则拒绝。
 */
@Service
class OAuthConnectionService(
    private val userRepository: UserRepository,
    private val connectionRepository: UserConnectionRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun listConnections(userId: Long): List<UserConnection> = connectionRepository.findAllByUserId(userId)

    /**
     * 登录流程：返回外部身份对应的皮肤站用户，不存在时创建。
     * 若提供商身份此前已绑定其他用户，登录即找回该账号，不视为冲突。
     */
    @Transactional
    fun findOrCreateUser(provider: String, identity: OAuthIdentity): User {
        connectionRepository.findByProviderAndOpenId(provider, identity.openId)?.let { connection ->
            return userRepository.findUserById(connection.userId)
                ?: throw ParamErrorException("绑定记录指向的用户不存在，请联系管理员")
        }
        return try {
            createUser(provider, identity)
        } catch (_: DataIntegrityViolationException) {
            // 并发首次登录时两条请求可能同时建号，唯一约束保证只有一个胜出，这里回头复用胜出者。
            val connection = connectionRepository.findByProviderAndOpenId(provider, identity.openId)
                ?: throw UserAlreadyExistsException("第三方账号注册冲突，请稍后重试")
            userRepository.findUserById(connection.userId)
                ?: throw ParamErrorException("绑定记录指向的用户不存在，请联系管理员")
        }
    }

    /** 绑定流程：把外部身份挂到 [userId] 名下；该外部身份已属于别人时报冲突。 */
    @Transactional
    fun bind(userId: Long, provider: String, identity: OAuthIdentity): UserConnection {
        connectionRepository.findByProviderAndOpenId(provider, identity.openId)?.let { existing ->
            if (existing.userId == userId) throw ParamErrorException("该第三方账号已绑定当前用户")
            throw UserAlreadyExistsException("该第三方账号已绑定其他用户")
        }
        if (connectionRepository.findByUserIdAndProvider(userId, provider) != null) {
            throw ParamErrorException("当前用户已绑定该提供商的账号，请先解绑")
        }
        val connection = UserConnection(
            userId = userId,
            provider = provider,
            openId = identity.openId,
            unionId = identity.unionId,
            nickname = identity.nickname,
            avatarUrl = identity.avatarUrl,
            createdAt = LocalDateTime.now(),
        )
        return connectionRepository.save(connection)
    }

    /**
     * 解绑流程：占位邮箱用户（无法用邮箱+密码登录找回）解绑最后一个连接后会被锁死在账号外，必须拒绝。
     * 正常注册用户总是保留密码这条退路，允许随时解绑。
     */
    @Transactional
    fun unbind(userId: Long, provider: String) {
        val connection = connectionRepository.findByUserIdAndProvider(userId, provider)
            ?: throw ParamErrorException("当前用户未绑定该提供商的账号")
        val user = userRepository.findUserById(userId) ?: throw ParamErrorException("用户不存在")
        val isSoleCredential = user.email.endsWith(NOREPLY_EMAIL_SUFFIX) &&
            connectionRepository.findAllByUserId(userId).size == 1
        if (isSoleCredential) {
            throw ParamErrorException("该账号仅剩这一种登录方式，请先绑定其他账号或完善邮箱后再解绑")
        }
        connectionRepository.delete(connection)
    }

    private fun createUser(provider: String, identity: OAuthIdentity): User {
        val nickname = identity.nickname.take(NICKNAME_MAX_LENGTH)
        val username = uniqueUsername(usernameSeed(provider, identity))
        val email = identity.email?.lowercase()?.takeIf { candidate ->
            EMAIL_PATTERN.matches(candidate) && userRepository.findByEmail(candidate) == null
        } ?: placeholderEmail(provider, identity.openId)
        val user = User(
            email = email,
            password = requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString())) { "密码加密失败" },
            username = username,
            nickname = nickname.ifBlank { username },
            verified = false,
            verificationToken = "",
        )
        val saved = userRepository.save(user)
        connectionRepository.save(
            UserConnection(
                userId = requireNotNull(saved.id),
                provider = provider,
                openId = identity.openId,
                unionId = identity.unionId,
                nickname = identity.nickname,
                avatarUrl = identity.avatarUrl,
                createdAt = LocalDateTime.now(),
            ),
        )
        return saved
    }

    /** 用户名候选顺序：昵称 → openId 摘要，清洗后保证落在站点用户名规则内。 */
    private fun usernameSeed(provider: String, identity: OAuthIdentity): String {
        val candidate = identity.nickname.ifBlank { "${provider}_${identity.openId.take(12)}" }
        val cleaned = candidate.filter { it.isLetterOrDigit() || it == '_' }.take(USERNAME_MAX_LENGTH)
        return cleaned.ifBlank { "user${UUID.randomUUID().toString().take(8)}" }
    }

    private fun uniqueUsername(seed: String): String {
        if (userRepository.findByUsername(seed) == null) return seed
        repeat(UNIQUE_RETRY) {
            val candidate = "${seed.take(USERNAME_MAX_LENGTH - 5)}_${UUID.randomUUID().toString().take(4)}"
            if (userRepository.findByUsername(candidate) == null) return candidate
        }
        throw UserAlreadyExistsException("无法生成可用用户名，请稍后重试")
    }

    private fun placeholderEmail(provider: String, openId: String): String =
        "${provider}_${openId.take(48)}$NOREPLY_EMAIL_SUFFIX".lowercase()

    companion object {
        /** OAuth 自动注册用户使用占位邮箱；解绑保护与后续补全邮箱流程都以该后缀识别。 */
        const val NOREPLY_EMAIL_SUFFIX = "@users.noreply.foxskin.local"

        /** 站点用户名规则：字母/数字/下划线，最长 50。 */
        val USERNAME_PATTERN = Regex("^[A-Za-z0-9_]{2,50}$")

        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private const val USERNAME_MAX_LENGTH = 50
        private const val NICKNAME_MAX_LENGTH = 50
        private const val UNIQUE_RETRY = 8
    }
}
