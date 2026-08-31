package top.foxball.foxskinserver.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import top.foxball.foxskinserver.repository.UserRepository

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header?.startsWith("Bearer ", ignoreCase = true) == true && SecurityContextHolder.getContext().authentication == null) {
            val claims = jwtService.parse(header.substring(7).trim())
            if (claims?.type == "access") {
                userRepository.findUserById(claims.subject)?.let { user ->
                    if (user.permission != top.foxball.foxskinserver.entity.jdbc.User.BANNED) {
                        val principal = AuthenticatedUser.from(user)
                        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
                    }
                }
            }
        }
        chain.doFilter(request, response)
    }
}
