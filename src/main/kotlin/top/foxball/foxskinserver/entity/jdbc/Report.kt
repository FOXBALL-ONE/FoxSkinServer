package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 纹理举报记录实体。 */
@Entity
@Table(name = "reports")
class Report(
    /** 举报记录主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 被举报纹理主键。 */
    @Column(name = "tid", nullable = false)
    var textureId: Long = 0,

    /** 被举报纹理上传者用户主键。 */
    @Column(name = "uploader", nullable = false)
    var uploaderId: Long = 0,

    /** 举报人用户主键。 */
    @Column(name = "reporter", nullable = false)
    var reporterId: Long = 0,

    /** 举报原因。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    var reason: String = "",

    /** 举报处理状态。 */
    @Column(nullable = false)
    var status: Int = PENDING,

    /** 举报提交时间。 */
    @Column(name = "report_at", nullable = false)
    var reportAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        /** 待处理状态。 */
        const val PENDING = 0

        /** 已解决状态。 */
        const val RESOLVED = 1

        /** 已驳回状态。 */
        const val REJECTED = 2
    }
}
