package top.foxball.foxskinserver.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.Texture

/** 用于把角色绑定的纹理主键解析为材质哈希和文件信息。 */
interface TextureRepository : JpaRepository<Texture, Long> {
    @EntityGraph(attributePaths = ["file"])
    fun findByHash(hash: String): Texture?
    
    /** Profile 生成发生在事务外时也必须预加载关联文件，避免 open-in-view 关闭后的懒加载异常。 */
    @EntityGraph(attributePaths = ["file"])
    @Query("select t from Texture t where t.id = :id")
    fun findByIdWithFile(@Param("id") id: Long): Texture?
    
    fun findAllByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Texture>
    
    /**
     * 管理端皮肤检索。
     *
     * 披风虽然复用 textures 表，但由 Cape 实体按 type='cape' 承载，这里显式排除，避免皮肤列表重复出现披风。
     * [keyword] 传空串表示不按名称过滤，[uploaderId] 与 [publicTexture] 传 null 表示不过滤。
     */
    @Query(
        """
        select t from Texture t
        where t.type <> 'cape'
          and (:keyword = '' or lower(t.name) like lower(concat('%', :keyword, '%')))
          and (:uploaderId is null or t.uploaderId = :uploaderId)
          and (:publicTexture is null or t.publicTexture = :publicTexture)
        """
    )
    fun search(
        @Param("keyword") keyword: String,
        @Param("uploaderId") uploaderId: Long?,
        @Param("publicTexture") publicTexture: Boolean?,
        pageable: Pageable,
    ): Page<Texture>
    
    /** 上传者名下的全部皮肤，不含披风；用于用户彻底删除时一并清理。 */
    fun findAllByUploaderIdAndTypeNot(uploaderId: Long, type: String): List<Texture>
    
    /**
     * 皮肤库：只取公开材质。[kind] 传空串取全部，`cape` 取披风，`skin` 取除披风外的纹理。
     */
    @Query(
        """
        select t from Texture t
        where t.publicTexture = true
          and (:keyword = '' or lower(t.name) like lower(concat('%', :keyword, '%')))
          and (:kind = ''
               or (:kind = 'cape' and t.type = 'cape')
               or (:kind = 'skin' and t.type <> 'cape'))
        """
    )
    fun searchPublic(
        @Param("keyword") keyword: String,
        @Param("kind") kind: String,
        pageable: Pageable,
    ): Page<Texture>
}
