package top.foxball.foxskinserver.service

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
}
