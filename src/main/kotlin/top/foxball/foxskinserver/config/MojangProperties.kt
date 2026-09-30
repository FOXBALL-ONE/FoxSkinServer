package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Minecraft Services 正版身份校验配置。 */
@ConfigurationProperties(prefix = "shopmall.mojang")
data class MojangProperties(
    var enabled: Boolean = false,
    var runtimeToggle: Boolean = true,
    var cacheSeconds: Long = 30,
    var profileUrl: String = "https://api.minecraftservices.com/minecraft/profile",
    var entitlementUrl: String = "https://api.minecraftservices.com/entitlements/mcstore",
    var connectTimeoutMillis: Long = 2000,
    var readTimeoutMillis: Long = 3000,
)
