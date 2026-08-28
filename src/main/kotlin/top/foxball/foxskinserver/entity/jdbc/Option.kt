package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 皮肤站键值对配置实体。 */
@Entity
@Table(name = "options")
class Option(
    /** 配置记录主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 配置项名称。 */
    @Column(name = "option_name", nullable = false, length = 50)
    var name: String = "",

    /** 配置项内容，支持较长文本。 */
    @Column(name = "option_value", nullable = false, columnDefinition = "TEXT")
    var value: String = "",
)
