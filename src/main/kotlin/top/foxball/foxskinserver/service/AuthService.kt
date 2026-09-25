package top.foxball.foxskinserver.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.config.JwtProperties
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.handler.AccessTokenExpiredException
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.RefreshTokenExpiredException
import top.foxball.foxskinserver.handler.TokenInvalidException
import top.foxball.foxskinserver.handler.UserNotFoundException
import top.foxball.foxskinserver.handler.UsernameOrPasswordErrorException
import top.foxball.foxskinserver.security.AccessTokenRevocationStore
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.security.JwtService
import top.foxball.foxskinserver.security.RefreshTokenStore

data class IssuedTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long)

@Service
class AuthService(
    private val userRepository: top.foxball.foxskinserver.repository.UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenStore: RefreshTokenStore,
    private val accessTokenRevocationStore: AccessTokenRevocationStore,
    private val jwtProperties: JwtProperties,
) {
    fun login(usernameOrEmail: String, password: String): IssuedTokens {
        val identifier = usernameOrEmail.trim()
        val user = userRepository.findByEmail(identifier.lowercase())
            ?: userRepository.findByUsername(identifier)
            ?: throw UsernameOrPasswordErrorException()
        if (user.permission == User.BANNED) throw UsernameOrPasswordErrorException()
        if (!passwordEncoder.matches(password, user.password)) throw UsernameOrPasswordErrorException()
        return issue(AuthenticatedUser.from(user))
    }
    
    fun refresh(token: String): IssuedTokens {
        val claims = jwtService.parse(token) ?: throw RefreshTokenExpiredException()
        if (claims.type != "refresh") throw TokenInvalidException("需要 Refresh Token")
        val user = userRepository.findUserById(claims.subject) ?: throw TokenInvalidException()
        if (user.permission == User.BANNED) throw TokenInvalidException("用户已被禁用")
        if (!refreshTokenStore.consume(
                claims.tokenId,
                claims.subject
            )
        ) throw TokenInvalidException("Refresh Token 已失效")
        val principal = AuthenticatedUser.from(user)
        if (jwtProperties.refresh.rotate) return issue(principal)
        val access = jwtService.createAccessToken(principal)
        val remain = (claims.expiresAt - System.currentTimeMillis() / 1000).coerceAtLeast(1)
        refreshTokenStore.save(claims.tokenId, claims.subject, remain)
        return IssuedTokens(access, token, jwtProperties.access.ttlSeconds)
    }
    
    fun revoke(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        jwtService.parse(refreshToken)?.let { refreshTokenStore.revoke(it.tokenId) }
    }
    
    /**
     * 修改密码。
     *
     * 必须先校验原密码，否则令牌泄漏后可直接改密顶号；新密码与原密码相同也拒绝，避免误操作后无感。
     * 注意：原密码错误返回 400 而不是 401——401 会被前端当作会话失效并强制登出。
     */
    fun changePassword(userId: Long, currentPassword: String, newPassword: String) {
        if (newPassword.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            throw ParamErrorException("新密码长度需在 $PASSWORD_MIN_LENGTH-$PASSWORD_MAX_LENGTH 个字符之间")
        }
        if (newPassword.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES) {
            throw ParamErrorException("新密码过长，请改用更短的密码")
        }
        val user = userRepository.findUserById(userId) ?: throw UserNotFoundException()
        if (!passwordEncoder.matches(currentPassword, user.password)) {
            throw ParamErrorException("原密码不正确")
        }
        if (passwordEncoder.matches(newPassword, user.password)) {
            throw ParamErrorException("新密码不能与原密码相同")
        }
        user.password = requireNotNull(passwordEncoder.encode(newPassword)) { "密码加密失败" }
        userRepository.save(user)
        // 改密即登出所有设备：refresh token 直接删掉，已经把 access token 签发在前的也一并判废。
        refreshTokenStore.revokeAllForUser(userId)
        accessTokenRevocationStore.markCredentialsChanged(
            userId,
            System.currentTimeMillis() / 1000,
            jwtProperties.refresh.ttlSeconds,
        )
    }
    
    /** 签发一对新令牌；站内密码登录、OAuth 首次登录与注册完成后的自动登录共用。 */
    fun issue(user: AuthenticatedUser): IssuedTokens {
        val access = jwtService.createAccessToken(user)
        val refresh = jwtService.createRefreshToken(user)
        val claims = jwtService.parse(refresh) ?: throw AccessTokenExpiredException("无法创建令牌")
        refreshTokenStore.save(claims.tokenId, user.userId, jwtProperties.refresh.ttlSeconds)
        return IssuedTokens(access, refresh, jwtProperties.access.ttlSeconds)
    }
    
    private companion object {
        /** 密码字符数下限。 */
        const val PASSWORD_MIN_LENGTH = 8
        
        /** 密码字符数上限。 */
        const val PASSWORD_MAX_LENGTH = 64
        
        /** BCrypt 只处理前 72 字节，超过会导致编码器直接抛异常，这里提前拦下。 */
        const val PASSWORD_MAX_BYTES = 72
    }
}
