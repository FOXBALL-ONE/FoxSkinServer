package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.MinecraftServer

/** Minecraft 服务器列表查询仓储。 */
interface MinecraftServerRepository : JpaRepository<MinecraftServer, Long> {
    fun findAllByOrderByIdAsc(): List<MinecraftServer>
}
