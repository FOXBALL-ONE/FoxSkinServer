package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.MinecraftServer
import top.foxball.foxskinserver.repository.MinecraftServerRepository

@Service
class MinecraftServerService(private val repository: MinecraftServerRepository) {
    fun getMinecraftServerById(id: Long): MinecraftServer? = repository.findById(id).orElse(null)
    fun getMinecraftServersByOrderByIdAsc(): List<MinecraftServer> = repository.findAllByOrderByIdAsc()
    fun save(server: MinecraftServer): MinecraftServer = repository.save(server)
    fun saveAll(servers: Iterable<MinecraftServer>): List<MinecraftServer> = repository.saveAll(servers)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
    
    /** 管理端服务器列表。 */
    fun search(pageable: Pageable): Page<MinecraftServer> = repository.findAll(pageable)
    
    /** 管理端新增服务器，在线人数等运行态字段保持默认值由后续探测填充。 */
    fun create(
        name: String,
        address: String,
        port: Int,
        description: String?,
        icon: String?,
        maxPlayers: Int
    ): MinecraftServer =
        repository.save(
            MinecraftServer(
                name = name,
                address = address,
                port = port,
                description = description,
                icon = icon,
                maxPlayers = maxPlayers,
            )
        )
    
    /** 管理端更新服务器；传 null 的字段保持原值。 */
    fun update(
        id: Long,
        name: String?,
        address: String?,
        port: Int?,
        description: String?,
        icon: String?,
        maxPlayers: Int?,
    ): MinecraftServer? {
        val server = repository.findById(id).orElse(null) ?: return null
        name?.let { server.name = it }
        address?.let { server.address = it }
        port?.let { server.port = it }
        description?.let { server.description = it }
        icon?.let { server.icon = it }
        maxPlayers?.let { server.maxPlayers = it }
        return repository.save(server)
    }
}
