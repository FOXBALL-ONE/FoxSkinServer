package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/** Minecraft 玩家角色实体。 */
@Entity
@Table(name = "players")
class Player(
    /** 玩家角色主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pid")
    var id: Long? = null,
    
    /** 所属用户主键。 */
    @Column(name = "uid", nullable = false)
    var userId: Long = 0,
    
    /** Minecraft Profile UUID，由应用侧生成。 */
    @Column(name = "uuid", nullable = false, unique = true)
    var uuid: UUID = UUID.randomUUID(),
    
    /** Minecraft 玩家名称。 */
    @Column(nullable = false, length = 50)
    var name: String = "",
    
    /** 绑定的皮肤纹理主键，-1 表示未设置。 */
    @Column(name = "tid_skin", nullable = false)
    var skinTextureId: Long = -1,
    
    /** 绑定的披风纹理主键，0 表示未设置。 */
    @Column(name = "tid_cape", nullable = false)
    var capeTextureId: Long = 0,
    
    /** 玩家资料最后修改时间。 */
    @Column(name = "last_modified", nullable = false)
    var lastModified: LocalDateTime = LocalDateTime.now(),
)
