package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/** Blessing Skin 披风实体；披风与皮肤共用 textures 表，通过 type=cape 区分。 */
@Entity
@Table(name = "textures")
@SQLRestriction("type = 'cape'")
class Cape(
    /** 纹理主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tid")
    var id: Long? = null,

    /** 实际披风文件；与 file_metadata 关联，允许为空以兼容历史纹理记录。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    var file: StoredFile? = null,

    /** 披风显示名称。 */
    @Column(nullable = false, length = 50)
    var name: String = "",

    /** 纹理类型，披风固定为 cape。 */
    @Column(nullable = false, length = 10)
    var type: String = TYPE,

    /** 披风文件内容的 SHA-256 哈希值。 */
    @Column(nullable = false, length = 64)
    var hash: String = "",

    /** 披风文件大小，单位为字节。 */
    @Column(nullable = false)
    var size: Long = 0,

    /** 上传者用户主键。 */
    @Column(name = "uploader", nullable = false)
    var uploaderId: Long = 0,

    /** 披风是否公开展示。 */
    @Column(name = "public", nullable = false)
    var publicTexture: Boolean = false,

    /** 披风上传时间。 */
    @Column(name = "upload_at", nullable = false)
    var uploadAt: LocalDateTime = LocalDateTime.now(),

    /** 披风获得的收藏数量。 */
    @Column(nullable = false)
    var likes: Int = 0,
) {
    /** 防止通过 Cape 实体写入非披风纹理。 */
    @PrePersist
    @PreUpdate
    fun enforceCapeType() {
        type = TYPE
    }

    companion object {
        /** Blessing Skin textures.type 对应的披风值。 */
        const val TYPE = "cape"
    }
}
