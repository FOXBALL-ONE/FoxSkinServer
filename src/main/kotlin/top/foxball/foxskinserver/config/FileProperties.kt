package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "file")
data class FileProperties(
    var storagePath: String = "./storage",
    var baseUrl: String = "http://localhost:8080",
    var signingSecret: String = "change-me",
    var downloadTokenTtlSeconds: Long = 300,
    var maxBatchSize: Int = 20,
    var maxFileSizeBytes: Long = 104857600,
    var signing: Signing = Signing(),
) {
    data class Signing(
        var publicTtlSeconds: Long = 60,
        var userTtlSeconds: Long = 300,
        var adminTtlSeconds: Long = 180,
        var orderTtlSeconds: Long = 300,
    ) {
        fun resolvedUserTtl(fallback: Long): Long = if (userTtlSeconds > 0) userTtlSeconds else fallback
    }
}
