package top.foxball.foxskinserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import top.foxball.foxskinserver.config.FileProperties
import top.foxball.foxskinserver.config.YggdrasilProperties

@SpringBootApplication
@EnableConfigurationProperties(FileProperties::class, YggdrasilProperties::class)
class FoxSkinServerApplication

fun main(args: Array<String>) {
    runApplication<FoxSkinServerApplication>(*args)
}
