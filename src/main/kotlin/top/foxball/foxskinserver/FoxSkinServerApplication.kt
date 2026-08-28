package top.foxball.foxskinserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FoxSkinServerApplication

fun main(args: Array<String>) {
    runApplication<FoxSkinServerApplication>(*args)
}
