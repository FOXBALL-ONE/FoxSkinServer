package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "shopmall.security.totp")
data class TotpProperties(
    var encryptionKey: String = "",
    var backupPepper: String = "",
    var issuer: String = "FoxSkin",
    var periodSeconds: Long = 30,
    var window: Int = 1,
    var digits: Int = 6,
    var challengeTtlSeconds: Long = 300,
    var maxChallengeAttempts: Int = 5,
)
