package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shopmall.security.jwt")
data class JwtProperties(
    var secret: String = "change-me-change-me-change-me-change-me",
    var access: Access = Access(),
    var refresh: Refresh = Refresh(),
) {
    data class Access(var ttlSeconds: Long = 1800)
    
    data class Refresh(
        var ttlSeconds: Long = 604800,
        var rotate: Boolean = true,
        var reuseDetect: Boolean = true,
        var graceSeconds: Long = 30,
        var cookie: Cookie = Cookie(),
    )
    
    data class Cookie(
        var name: String = "refresh_token",
        var domain: String = "",
        var path: String = "/api/auth",
        var secure: Boolean = true,
        var httpOnly: Boolean = true,
        var sameSite: String = "Lax",
    )
}
