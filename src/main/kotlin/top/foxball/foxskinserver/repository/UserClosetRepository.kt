package top.foxball.foxskinserver.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.UserCloset
import top.foxball.foxskinserver.entity.jdbc.UserClosetId
import java.time.LocalDateTime

/** 衣柜条目连同所指向材质的展示信息。键名需与 JPQL 别名一致，Spring Data 才能完成映射。 */
interface ClosetEntryView {
    fun getTextureId(): Long
    fun getItemName(): String?
    fun getTextureName(): String
    fun getTextureType(): String
    fun getTextureHash(): String
    fun getTextureSize(): Long
    fun getTextureUploaderId(): Long
    fun getTexturePublic(): Boolean
    fun getTextureUploadAt(): LocalDateTime
    fun getTextureLikes(): Int
}

/** 用户衣柜关联数据访问仓储。 */
interface UserClosetRepository : JpaRepository<UserCloset, UserClosetId> {
    fun findUserClosetByIdUserIdAndIdTextureId(userId: Long, textureId: Long): UserCloset?
    
    /** 用户衣柜内的全部关联记录；用于用户彻底删除时一并清理。 */
    fun findAllByIdUserId(userId: Long): List<UserCloset>
    
    /**
     * 衣柜列表：把衣柜条目和它指向的材质连起来，一次查询完成筛选与分页。
     *
     * [kind] 传空串表示不筛选，`cape` 只取披风，`skin` 取除披风外的全部纹理——披风与皮肤共用 textures 表，
     * 只能用 type 区分。材质不 JOIN 实体而是显式列出字段，避免 open-in-view 关闭后触发懒加载。
     */
    @Query(
        """
        select c.id.textureId as textureId,
               c.itemName as itemName,
               t.name as textureName,
               t.type as textureType,
               t.hash as textureHash,
               t.size as textureSize,
               t.uploaderId as textureUploaderId,
               t.publicTexture as texturePublic,
               t.uploadAt as textureUploadAt,
               t.likes as textureLikes
        from UserCloset c join Texture t on t.id = c.id.textureId
        where c.id.userId = :userId
          and (:kind = ''
               or (:kind = 'cape' and t.type = 'cape')
               or (:kind = 'skin' and t.type <> 'cape'))
          and (:keyword = ''
               or lower(t.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(c.itemName, '')) like lower(concat('%', :keyword, '%')))
        """
    )
    fun searchCloset(
        @Param("userId") userId: Long,
        @Param("kind") kind: String,
        @Param("keyword") keyword: String,
        pageable: Pageable,
    ): Page<ClosetEntryView>
}
