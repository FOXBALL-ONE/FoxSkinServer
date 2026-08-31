package top.foxball.foxskinserver.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestParam
import top.foxball.foxskinserver.service.PlayerService
import top.foxball.foxskinserver.service.UserService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder

@RestController
class UserController(
    private val userService: UserService,
    private val playerService: PlayerService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/api/users/{id}")
    @PreAuthorize("#id == authentication.principal.userId or hasRole('ADMIN')")
    fun getUser(@PathVariable("id") id: Long): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String
        )
        
        val user = userService.findById(id)
            ?: return responseBuilder.notFound().build()
        val rs = UserData(
            requireNotNull(user.id),
            user.username,
            user.email
        )
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/api/users/by-player")
    @PreAuthorize("hasRole('ADMIN')")
    fun getUserByPlayerName(
        @RequestParam("player_name") playerName: String,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
        )

        val player = playerService.getPlayerByName(playerName)
            ?: return responseBuilder.notFound().build()
        val user = userService.findById(player.userId)
            ?: return responseBuilder.notFound().build()
        val rs = UserData(
            requireNotNull(user.id),
            user.username,
            user.email,
        )
        return responseBuilder.ok().data(rs).build()
    }
}
