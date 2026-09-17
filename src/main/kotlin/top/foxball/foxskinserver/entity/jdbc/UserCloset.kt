package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** 用户收藏纹理的关联实体。 */
@Entity
@Table(name = "user_closet")
class UserCloset(
    /** 用户与纹理关联表的联合主键。 */
    @EmbeddedId
    var id: UserClosetId = UserClosetId(),
    
    /** 用户在衣柜中为纹理设置的名称。 */
    @Column(name = "item_name", columnDefinition = "TEXT")
    var itemName: String? = null,
)

/** 用户收藏纹理关联表的联合主键。 */
@Embeddable
data class UserClosetId(
    /** 用户主键。 */
    @Column(name = "user_uid")
    var userId: Long = 0,
    
    /** 纹理主键。 */
    @Column(name = "texture_tid")
    var textureId: Long = 0,
)
