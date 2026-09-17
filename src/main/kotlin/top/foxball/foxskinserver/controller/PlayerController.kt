package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.PlayerService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 角色管理：一个用户可以拥有多个 Minecraft 角色，每个角色各自绑定皮肤与披风。
 *
 * @folder 用户/角色
 */
@RestController
@RequestMapping("/api/users/me/players")
class PlayerController(
    private val playerService: PlayerService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 我的角色列表
     */
    @GetMapping
    fun listPlayers(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Response> {
        data class PlayerData(
            val id: Long,
            val name: String,
            val uuid: String,
            @param:JsonProperty("skin_texture_id")
            val skinTextureId: Long,
            @param:JsonProperty("skin_hash")
            val skinHash: String?,
            @param:JsonProperty("cape_texture_id")
            val capeTextureId: Long,
            @param:JsonProperty("cape_hash")
            val capeHash: String?,
            @param:JsonProperty("last_modified")
            val lastModified: LocalDateTime,
        )
        
        data class Response(
            val list: List<PlayerData>,
        )
        
        val list = playerService.getPlayersWithTextures(principal.userId).map { entry ->
            val player = entry.player
            PlayerData(
                id = requireNotNull(player.id),
                name = player.name,
                uuid = player.uuid.toString(),
                skinTextureId = player.skinTextureId,
                skinHash = entry.skinHash,
                capeTextureId = player.capeTextureId,
                capeHash = entry.capeHash,
                lastModified = player.lastModified,
            )
        }
        val rs = Response(list)
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 新建角色
     * @param name 角色名，只允许字母、数字和下划线，长度 2-16 位
     */
    @PostMapping
    fun createPlayer(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam("name") name: String,
    ): ResponseEntity<Response> {
        data class PlayerData(
            val id: Long,
            val name: String,
            val uuid: String,
            @param:JsonProperty("skin_texture_id")
            val skinTextureId: Long,
            @param:JsonProperty("cape_texture_id")
            val capeTextureId: Long,
            @param:JsonProperty("last_modified")
            val lastModified: LocalDateTime,
        )
        
        val player = playerService.createPlayer(principal.userId, name)
        val rs = PlayerData(
            id = requireNotNull(player.id),
            name = player.name,
            uuid = player.uuid.toString(),
            skinTextureId = player.skinTextureId,
            capeTextureId = player.capeTextureId,
            lastModified = player.lastModified,
        )
        return builder.created()
            .data(rs)
            .build()
    }
    
    /**
     * @api 重命名角色
     * @param playerId 角色主键
     * @param name 新的角色名
     */
    @PatchMapping("/{player_id}")
    fun renamePlayer(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("player_id") playerId: Long,
        @RequestParam("name") name: String,
    ): ResponseEntity<Response> {
        data class PlayerData(
            val id: Long,
            val name: String,
            val uuid: String,
            @param:JsonProperty("last_modified")
            val lastModified: LocalDateTime,
        )
        
        val player = playerService.renamePlayer(principal.userId, playerId, name)
        val rs = PlayerData(
            id = requireNotNull(player.id),
            name = player.name,
            uuid = player.uuid.toString(),
            lastModified = player.lastModified,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 为角色设置皮肤与披风
     *
     * 省略参数表示保持原绑定；皮肤传 -1、披风传 0 表示解除绑定。
     *
     * @param playerId 角色主键
     * @param skinTextureId 皮肤材质主键
     * @param capeTextureId 披风材质主键
     */
    @PutMapping("/{player_id}/textures")
    fun setPlayerTextures(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("player_id") playerId: Long,
        @RequestParam("skin_texture_id", required = false) skinTextureId: Long?,
        @RequestParam("cape_texture_id", required = false) capeTextureId: Long?,
    ): ResponseEntity<Response> {
        data class PlayerData(
            val id: Long,
            val name: String,
            val uuid: String,
            @param:JsonProperty("skin_texture_id")
            val skinTextureId: Long,
            @param:JsonProperty("cape_texture_id")
            val capeTextureId: Long,
            @param:JsonProperty("last_modified")
            val lastModified: LocalDateTime,
        )
        
        val player = playerService.setPlayerTextures(principal.userId, playerId, skinTextureId, capeTextureId)
        val rs = PlayerData(
            id = requireNotNull(player.id),
            name = player.name,
            uuid = player.uuid.toString(),
            skinTextureId = player.skinTextureId,
            capeTextureId = player.capeTextureId,
            lastModified = player.lastModified,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 删除角色
     * @param playerId 角色主键
     */
    @DeleteMapping("/{player_id}")
    fun deletePlayer(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("player_id") playerId: Long,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val deleted: Boolean,
        )
        
        playerService.deletePlayer(principal.userId, playerId)
        val rs = Response(playerId, true)
        return builder.ok()
            .data(rs)
            .build()
    }
}
