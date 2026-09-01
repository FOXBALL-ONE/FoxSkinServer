package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.LocalDateTime

/** Blessing Skin 兼容的用户实体。 */
@Entity
@Table(name = "users")
class User(
    /** 用户主键，对应 Blessing Skin 的 uid。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uid")
    var id: Long? = null,
    
    /** 用户登录邮箱。 */
    @Column(nullable = false, unique = true, length = 100)
    var email: String = "",
    
    /** 用户密码哈希值，不保存明文密码。 */
    @Column(nullable = false, length = 255)
    var password: String = "",
    
    /** 用户名，用于登录，系统内唯一。 */
    @Column(nullable = false, unique = true, length = 50)
    var username: String = "",

    /** 用户昵称，用于展示。 */
    @Column(nullable = false, length = 50)
    var nickname: String = "",
    
    /** 用户界面语言，为空时使用站点默认语言。 */
    @Column(nullable = true, length = 255)
    var locale: String? = null,
    
    /** 用户头像资源标识。 */
    @Column(nullable = false)
    var avatar: Int = 0,
    
    /** 用户积分，用于请求频率控制等业务规则。 */
    @Column(nullable = false)
    var score: Int = 0,
    
    /** 用户权限等级。 */
    @Column(nullable = false)
    var permission: Int = NORMAL,
    
    /** 用户最近一次登录时记录的 IP 地址。 */
    @Column(nullable = false, length = 45)
    var ip: String = "",
    
    /** 用户最近一次登录时间。 */
    @Column(name = "last_sign_at", nullable = false)
    var lastSignAt: LocalDateTime = LocalDateTime.now(),
    
    /** 用户注册时间。 */
    @Column(name = "register_at", nullable = false)
    var registerAt: LocalDateTime = LocalDateTime.now(),
    
    /** 用户是否已完成邮箱验证。 */
    @Column(nullable = false)
    var verified: Boolean = false,
    
    /** 用户邮箱验证令牌。 */
    @Column(name = "verification_token", nullable = false, length = 255)
    var verificationToken: String = "",
    
    /** 用于记住登录状态的令牌。 */
    @Column(name = "remember_token", length = 100)
    var rememberToken: String? = null,
    
    /** 用户是否启用深色模式。 */
    @Column(name = "is_dark_mode", nullable = false)
    var isDarkMode: Boolean = false,
) {
    /** 兼容 Blessing Skin 命名的用户主键别名。 */
    @get:Transient
    val uid: Long?
        get() = id

    /** 判断用户是否拥有管理员权限。 */
    fun isAdmin(): Boolean = permission >= ADMIN

    companion object {
        /** 封禁用户权限等级。 */
        const val BANNED = -1

        /** 普通用户权限等级。 */
        const val NORMAL = 0

        /** 管理员权限等级。 */
        const val ADMIN = 1

        /** 超级管理员权限等级。 */
        const val SUPER_ADMIN = 2
    }
}
