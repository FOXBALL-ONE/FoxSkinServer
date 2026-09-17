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
    
    /** 忽略大小写匹配名称。players.name 没有唯一约束，历史数据可能重名，所以返回列表而不是单个。 */
    fun findAllByNameIgnoreCase(name: String): List<Player>
}
