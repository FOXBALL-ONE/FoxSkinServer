package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 站内通知实体。 */
@Entity
@Table(name = "notifications")
class Notification(
    /** 通知 UUID。 */
    @Id
    @Column(length = 36)
    var id: String = "",
    
    /** 通知类型。 */
    @Column(nullable = false, length = 255)
    var type: String = "",
    
    /** 被通知对象的实体类型。 */
    @Column(name = "notifiable_type", nullable = false, length = 255)
    var notifiableType: String = "",
    
    /** 被通知对象的主键。 */
    @Column(name = "notifiable_id", nullable = false)
    var notifiableId: Long = 0,
    
    /** 通知载荷，通常为 JSON 文本。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    var data: String = "",
    
    /** 通知阅读时间，未阅读时为空。 */
    @Column(name = "read_at")
    var readAt: LocalDateTime? = null,
    
    /** 通知创建时间。 */
    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,
    
    /** 通知最后更新时间。 */
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,
)
