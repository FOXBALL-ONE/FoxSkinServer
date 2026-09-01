package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shopmall.security.default-admin")
data class DefaultAdminProperties(
    var enabled: Boolean = false,
    var email: String = "",
    var username: String = "",
    var fixedToken: FixedToken = FixedToken(),
    var password: String = "",
) {
    data class FixedToken(
        var enabled: Boolean = false,
        var token: String = "",
    )
}
