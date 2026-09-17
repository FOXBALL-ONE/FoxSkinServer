package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/** Minecraft 皮肤或披风纹理实体。 */
@Entity
@Table(name = "textures")
class Texture(
    /** 纹理主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tid")
    var id: Long? = null,
    
    /** 实际纹理文件；与 file_metadata 关联，允许为空以兼容历史纹理记录。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    var file: StoredFile? = null,
    
    /** 纹理显示名称。 */
    @Column(nullable = false, length = 50)
    var name: String = "",
    
    /** 纹理类型，例如 Steve、Alex 或披风。 */
    @Column(nullable = false, length = 10)
    var type: String = "",
    
    /** 纹理文件内容的 SHA-256 哈希值。 */
    @Column(nullable = false, length = 64)
    var hash: String = "",
    
    /** 纹理文件大小，单位为字节。 */
    @Column(nullable = false)
    var size: Long = 0,
    
    /** 上传者用户主键。 */
    @Column(name = "uploader", nullable = false)
    var uploaderId: Long = 0,
    
    /** 纹理是否公开展示。 */
    @Column(name = "public", nullable = false)
    var publicTexture: Boolean = false,
    
    /** 纹理上传时间。 */
    @Column(name = "upload_at", nullable = false)
    var uploadAt: LocalDateTime = LocalDateTime.now(),
    
    /** 纹理获得的收藏数量。 */
    @Column(nullable = false)
    var likes: Int = 0,
)
