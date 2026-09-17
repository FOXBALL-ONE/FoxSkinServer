package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 数据库翻译文本实体。 */
@Entity
@Table(name = "language_lines")
class LanguageLine(
    /** 翻译文本主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    /** 翻译分组名称。 */
    @Column(name = "group", nullable = false, length = 255)
    var group: String = "",
    
    /** 翻译键名。 */
    @Column(name = "key", nullable = false, length = 255)
    var key: String = "",
    
    /** 翻译文本内容。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    var text: String = "",
    
    /** 翻译记录创建时间。 */
    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,
    
    /** 翻译记录最后更新时间。 */
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,
)
