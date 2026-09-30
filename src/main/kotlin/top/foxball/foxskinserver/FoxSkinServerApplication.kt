package top.foxball.foxskinserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import top.foxball.foxskinserver.config.DefaultAdminProperties
import top.foxball.foxskinserver.config.FileProperties
import top.foxball.foxskinserver.config.OAuthProperties
import top.foxball.foxskinserver.config.MojangProperties
import top.foxball.foxskinserver.config.YggdrasilProperties

@SpringBootApplication
@EnableConfigurationProperties(
    DefaultAdminProperties::class,
    FileProperties::class,
    OAuthProperties::class,
    MojangProperties::class,
    YggdrasilProperties::class,
)
class FoxSkinServerApplication

fun main(args: Array<String>) {
    runApplication<FoxSkinServerApplication>(*args)
}
