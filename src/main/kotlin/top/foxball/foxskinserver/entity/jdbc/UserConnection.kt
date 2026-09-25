package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/** 第三方登录（OAuth/OIDC）账号绑定实体，把皮肤站用户和外部平台的 OpenID 关联起来。 */
@Entity
@Table(
    name = "user_connections",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_connection_provider_open_id", columnNames = ["provider", "open_id"])],
    indexes = [Index(name = "ix_user_connection_user_id", columnList = "user_id")],
)
class UserConnection(
    /** 绑定记录主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    /** 绑定的皮肤站用户主键，对应 [User.id]。 */
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    
    /**
     * 提供商标识，对应扩展组件注册的组件 id（例如 `qq`、`oidc`）。
     * 同一类型 OIDC 站点通常只有一个，这里保留按组件 id 而非按协议类型存储。
     */
    @Column(nullable = false, length = 50)
    var provider: String = "",
    
    /** 提供商侧的用户唯一标识（QQ 为 OpenID，OIDC 为 `sub` 声明）。 */
    @Column(name = "open_id", nullable = false, length = 128)
    var openId: String = "",
    
    /** 提供商侧的跨应用用户标识（QQ UnionID，OIDC 场景为空），用于多应用归并同一身份。 */
    @Column(name = "union_id", nullable = true, length = 128)
    var unionId: String? = null,
    
    /** 绑定时从提供商获取的昵称，仅作展示参考，不回写皮肤站昵称。 */
    @Column(nullable = false, length = 255)
    var nickname: String = "",
    
    /** 绑定时从提供商获取的头像地址，仅作展示参考。 */
    @Column(name = "avatar_url", nullable = true, length = 512)
    var avatarUrl: String? = null,
    
    /** 绑定时间。 */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
