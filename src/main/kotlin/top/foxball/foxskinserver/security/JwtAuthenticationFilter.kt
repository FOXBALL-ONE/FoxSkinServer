package top.foxball.foxskinserver.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import top.foxball.foxskinserver.config.DefaultAdminProperties
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.repository.UserRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val defaultAdminProperties: DefaultAdminProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header?.startsWith("Bearer ", ignoreCase = true) == true && SecurityContextHolder.getContext().authentication == null) {
            val token = header.substring(7).trim()
            val fixedToken = defaultAdminProperties.fixedToken
            if (
                defaultAdminProperties.enabled &&
                fixedToken.enabled &&
                fixedToken.token.isNotBlank() &&
                MessageDigest.isEqual(
                    token.toByteArray(StandardCharsets.UTF_8),
                    fixedToken.token.toByteArray(StandardCharsets.UTF_8),
                )
            ) {
                userRepository.findByEmail(defaultAdminProperties.email.trim().lowercase())?.let { user ->
                    if (user.permission >= User.ADMIN) {
                        val principal = AuthenticatedUser.from(user)
                        SecurityContextHolder.getContext().authentication =
                            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
                    }
                }
            } else {
                val claims = jwtService.parse(token)
                if (claims?.type == "access") {
                    userRepository.findUserById(claims.subject)?.let { user ->
                        if (user.permission != User.BANNED) {
                            val principal = AuthenticatedUser.from(user)
                            SecurityContextHolder.getContext().authentication =
                                UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
                        }
                    }
                }
            }
        }
        chain.doFilter(request, response)
    }
}
