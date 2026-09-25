package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import top.foxball.foxskinserver.security.JwtAuthenticationFilter
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import top.foxball.foxskinserver.shared.Response

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    private val yggdrasilProperties: YggdrasilProperties,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
                // 第三方登录扩展组件：提供商列表与授权跳转/回调在登录前就要可达，绑定接口不在其列。
                "/api/auth/oauth/providers", "/api/auth/oauth/*/redirect", "/api/auth/oauth/*/callback",
                yggdrasilProperties.apiPath, "${yggdrasilProperties.apiPath}/**", "/textures/**", "/avatar/**",
                "/actuator/health", "/error",
            ).permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { _, response, _ -> writeError(response, 401, "未授权") }
                .accessDeniedHandler { _, response, _ -> writeError(response, 403, "禁止访问") }
        }
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()
    
    private fun writeError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(objectMapper.writeValueAsString(Response(status, message, emptyMap<String, Any>())))
    }
}
