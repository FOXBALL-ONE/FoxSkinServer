package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.config.JwtProperties
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.AuthService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder

@RestController
class AuthController(
    private val authService: AuthService,
    private val responseBuilder: ResponseBuilder,
    private val jwtProperties: JwtProperties,
) {
    @PostMapping("/api/auth/login")
    fun login(
        @RequestParam("email") email: String,
        @RequestParam("password") password: String,
        response: HttpServletResponse
    ): ResponseEntity<Response> {
        data class TokenData(
            @param:JsonProperty("access_token") val accessToken: String,
            @param:JsonProperty("refresh_token") val refreshToken: String,
            @param:JsonProperty("expires_in") val expiresIn: Long,
        )

        val tokens = authService.login(email, password)
        response.addHeader("Set-Cookie", refreshCookie(tokens.refreshToken).toString())
        return responseBuilder.ok().data(TokenData(tokens.accessToken, tokens.refreshToken, tokens.expiresIn)).build()
    }

    @PostMapping("/api/auth/refresh")
    fun refresh(
        request: HttpServletRequest,
        @RequestParam("refresh_token", required = false) refreshToken: String?,
        response: HttpServletResponse
    ): ResponseEntity<Response> {
        data class TokenData(
            @param:JsonProperty("access_token") val accessToken: String,
            @param:JsonProperty("refresh_token") val refreshToken: String,
            @param:JsonProperty("expires_in") val expiresIn: Long,
        )

        val cookieToken = request.cookies?.firstOrNull { it.name == jwtProperties.refresh.cookie.name }?.value
        val tokens = authService.refresh(cookieToken ?: refreshToken ?: "")
        response.addHeader("Set-Cookie", refreshCookie(tokens.refreshToken).toString())
        return responseBuilder.ok().data(TokenData(tokens.accessToken, tokens.refreshToken, tokens.expiresIn)).build()
    }

    @PostMapping("/api/auth/logout")
    fun logout(
        request: HttpServletRequest,
        @RequestParam("refresh_token", required = false) refreshToken: String?,
        response: HttpServletResponse
    ): ResponseEntity<Response> {
        val cookieToken = request.cookies?.firstOrNull { it.name == jwtProperties.refresh.cookie.name }?.value
        authService.revoke(cookieToken ?: refreshToken)
        response.addHeader("Set-Cookie", refreshCookie("", 0).toString())
        return responseBuilder.ok().build()
    }

    @org.springframework.web.bind.annotation.GetMapping("/api/auth/me")
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): ResponseEntity<Response> {
        data class UserData(
            @param:JsonProperty("user_id") val userId: Long,
            val email: String,
            val nickname: String,
            val permission: Int,
            val verified: Boolean,
        )
        return responseBuilder.ok().data(
            UserData(
                principal.userId,
                principal.email,
                principal.nickname,
                principal.permission,
                principal.verified
            )
        ).build()
    }

    private fun refreshCookie(token: String, maxAgeOverride: Long? = null): ResponseCookie {
        val config = jwtProperties.refresh.cookie
        val builder = ResponseCookie.from(config.name, token)
            .httpOnly(config.httpOnly)
            .secure(config.secure)
            .path(config.path)
            .sameSite(config.sameSite)
            .maxAge(maxAgeOverride ?: jwtProperties.refresh.ttlSeconds)
        if (config.domain.isNotBlank()) builder.domain(config.domain)
        return builder.build()
    }
}
