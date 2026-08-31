package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.Player
import java.util.UUID

/** Minecraft 角色查询仓储。UUID 和名称都是 Yggdrasil 协议的主要索引。 */
interface PlayerRepository : JpaRepository<Player, Long> {
    fun findByUuid(uuid: UUID): Player?
    fun findByName(name: String): Player?
    fun findAllByUserId(userId: Long): List<Player>
    fun findPlayerByUserIdAndName(userId: Long, name: String): Player?
}
