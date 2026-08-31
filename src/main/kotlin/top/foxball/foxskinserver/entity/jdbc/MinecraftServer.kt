package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 我的世界服务器基础信息实体，用于服务器列表展示。 */
@Entity
@Table(name = "minecraft_servers")
class MinecraftServer(
    /** 服务器记录主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 服务器展示名称。 */
    @Column(nullable = false, length = 100)
    var name: String = "",

    /** 服务器连接地址（域名或 IP）。 */
    @Column(nullable = false, length = 255)
    var address: String = "",

    /** 服务器连接端口。 */
    @Column(nullable = false)
    var port: Int = 25565,

    /** 服务器简介。 */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /** 服务器图标地址或资源标识。 */
    @Column(length = 512)
    var icon: String? = null,

    /** 服务器当前是否在线。 */
    @Column(nullable = false)
    var online: Boolean = false,

    /** 当前在线玩家数量。 */
    @Column(name = "online_players", nullable = false)
    var onlinePlayers: Int = 0,

    /** 服务器允许的最大玩家数量。 */
    @Column(name = "max_players", nullable = false)
    var maxPlayers: Int = 0,

    /** 服务器版本号。 */
    @Column(length = 50)
    var version: String? = null,

    /** 服务器信息创建时间。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** 服务器信息最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
