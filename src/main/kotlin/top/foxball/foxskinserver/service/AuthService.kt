package top.foxball.foxskinserver.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.config.JwtProperties
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.handler.AccessTokenExpiredException
import top.foxball.foxskinserver.handler.RefreshTokenExpiredException
import top.foxball.foxskinserver.handler.TokenInvalidException
import top.foxball.foxskinserver.handler.UsernameOrPasswordErrorException
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
    private val jwtProperties: JwtProperties,
) {
    fun login(email: String, password: String): IssuedTokens {
        val user = userRepository.findByEmail(email.trim().lowercase()) ?: throw UsernameOrPasswordErrorException()
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

    private fun issue(user: AuthenticatedUser): IssuedTokens {
        val access = jwtService.createAccessToken(user)
        val refresh = jwtService.createRefreshToken(user)
        val claims = jwtService.parse(refresh) ?: throw AccessTokenExpiredException("无法创建令牌")
        refreshTokenStore.save(claims.tokenId, user.userId, jwtProperties.refresh.ttlSeconds)
        return IssuedTokens(access, refresh, jwtProperties.access.ttlSeconds)
    }
}
