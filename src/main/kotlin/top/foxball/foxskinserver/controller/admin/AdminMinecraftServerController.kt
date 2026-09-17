package top.foxball.foxskinserver.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.MinecraftServerService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder

/**
 * 管理端 Minecraft 服务器接口，仅管理员可访问。
 *
 * 在线状态与在线人数由后续探测写入，管理端只维护展示信息。
 *
 * @folder 管理端/服务器
 */
@Validated
@RestController
@RequestMapping("/api/admin/servers")
@PreAuthorize("hasRole('ADMIN')")
class AdminMinecraftServerController(
    private val serverService: MinecraftServerService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 服务器列表
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listServers(
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "25") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class ServerData(
            val id: Long,
            val name: String,
            val address: String,
            val port: Int,
            val description: String?,
            val icon: String?,
            val online: Boolean,
            @param:JsonProperty("online_players")
            val onlinePlayers: Int,
            @param:JsonProperty("max_players")
            val maxPlayers: Int,
        )
        
        data class Pagination(
            val page: Int,
            val size: Int,
            val total: Long,
            @param:JsonProperty("total_pages")
            val totalPages: Int,
        )
        
        data class Response(
            val list: List<ServerData>,
            val pagination: Pagination,
        )
        
        val paged = serverService.search(
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "id")),
        )
        val list = paged.content.map { server ->
            ServerData(
                id = requireNotNull(server.id),
                name = server.name,
                address = server.address,
                port = server.port,
                description = server.description,
                icon = server.icon,
                online = server.online,
                onlinePlayers = server.onlinePlayers,
                maxPlayers = server.maxPlayers,
            )
        }
        val rs = Response(
            list,
            Pagination(paged.number + 1, paged.size, paged.totalElements, paged.totalPages),
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 服务器详情
     * @param serverId 服务器主键
     */
    @GetMapping("/{server_id}")
    fun getServer(@PathVariable("server_id") serverId: Long): ResponseEntity<Response> {
        data class ServerData(
            val id: Long,
            val name: String,
            val address: String,
            val port: Int,
            val description: String?,
            val icon: String?,
            val online: Boolean,
            @param:JsonProperty("online_players")
            val onlinePlayers: Int,
            @param:JsonProperty("max_players")
            val maxPlayers: Int,
        )
        
        val server = serverService.getMinecraftServerById(serverId)
            ?: return builder.notFound().build()
        val rs = ServerData(
            id = requireNotNull(server.id),
            name = server.name,
            address = server.address,
            port = server.port,
            description = server.description,
            icon = server.icon,
            online = server.online,
            onlinePlayers = server.onlinePlayers,
            maxPlayers = server.maxPlayers,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 新增服务器
     * @param name 服务器展示名称
     * @param address 服务器连接地址
     * @param port 服务器连接端口
     * @param description 服务器简介
     * @param icon 服务器图标地址
     * @param maxPlayers 服务器允许的最大玩家数量
     */
    @PostMapping
    fun createServer(
        @RequestParam("name") name: String,
        @RequestParam("address") address: String,
        @RequestParam("port", defaultValue = "25565") @Min(1) @Max(65535) port: Int,
        @RequestParam("description", required = false) description: String?,
        @RequestParam("icon", required = false) icon: String?,
        @RequestParam("max_players", defaultValue = "0") @Min(0) maxPlayers: Int,
    ): ResponseEntity<Response> {
        data class ServerData(
            val id: Long,
            val name: String,
            val address: String,
            val port: Int,
            val description: String?,
            val icon: String?,
            val online: Boolean,
            @param:JsonProperty("online_players")
            val onlinePlayers: Int,
            @param:JsonProperty("max_players")
            val maxPlayers: Int,
        )
        
        val server = serverService.create(name.trim(), address.trim(), port, description, icon, maxPlayers)
        val rs = ServerData(
            id = requireNotNull(server.id),
            name = server.name,
            address = server.address,
            port = server.port,
            description = server.description,
            icon = server.icon,
            online = server.online,
            onlinePlayers = server.onlinePlayers,
            maxPlayers = server.maxPlayers,
        )
        return builder.created()
            .data(rs)
            .build()
    }
    
    /**
     * @api 更新服务器
     *
     * 只更新传入的字段，省略的字段保持原值。
     *
     * @param serverId 服务器主键
     * @param name 服务器展示名称
     * @param address 服务器连接地址
     * @param port 服务器连接端口
     * @param description 服务器简介
     * @param icon 服务器图标地址
     * @param maxPlayers 服务器允许的最大玩家数量
     */
    @PatchMapping("/{server_id}")
    fun updateServer(
        @PathVariable("server_id") serverId: Long,
        @RequestParam("name", required = false) name: String?,
        @RequestParam("address", required = false) address: String?,
        @RequestParam("port", required = false) @Min(1) @Max(65535) port: Int?,
        @RequestParam("description", required = false) description: String?,
        @RequestParam("icon", required = false) icon: String?,
        @RequestParam("max_players", required = false) @Min(0) maxPlayers: Int?,
    ): ResponseEntity<Response> {
        data class ServerData(
            val id: Long,
            val name: String,
            val address: String,
            val port: Int,
            val description: String?,
            val icon: String?,
            val online: Boolean,
            @param:JsonProperty("online_players")
            val onlinePlayers: Int,
            @param:JsonProperty("max_players")
            val maxPlayers: Int,
        )
        
        val server = serverService.update(
            serverId,
            name?.trim(),
            address?.trim(),
            port,
            description,
            icon,
            maxPlayers,
        )
            ?: return builder.notFound().build()
        val rs = ServerData(
            id = requireNotNull(server.id),
            name = server.name,
            address = server.address,
            port = server.port,
            description = server.description,
            icon = server.icon,
            online = server.online,
            onlinePlayers = server.onlinePlayers,
            maxPlayers = server.maxPlayers,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 删除服务器
     * @param serverId 服务器主键
     */
    @DeleteMapping("/{server_id}")
    fun deleteServer(@PathVariable("server_id") serverId: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val deleted: Boolean,
        )
        
        serverService.getMinecraftServerById(serverId)
            ?: return builder.notFound().build()
        serverService.deleteById(serverId)
        val rs = Response(serverId, true)
        return builder.ok()
            .data(rs)
            .build()
    }
}
