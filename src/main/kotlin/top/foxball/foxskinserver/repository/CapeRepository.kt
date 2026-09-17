package top.foxball.foxskinserver.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.Cape

/** 披风纹理数据访问仓储。 */
interface CapeRepository : JpaRepository<Cape, Long> {
    fun findAllByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Cape>
    
    /**
     * 管理端披风检索；实体自带 type='cape' 限制，无需再按类型过滤。
     * [keyword] 传空串表示不按名称过滤，[uploaderId] 与 [publicTexture] 传 null 表示不过滤。
     */
    @Query(
        """
        select c from Cape c
        where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
          and (:uploaderId is null or c.uploaderId = :uploaderId)
          and (:publicTexture is null or c.publicTexture = :publicTexture)
        """
    )
    fun search(
        @Param("keyword") keyword: String,
        @Param("uploaderId") uploaderId: Long?,
        @Param("publicTexture") publicTexture: Boolean?,
        pageable: Pageable,
    ): Page<Cape>
    
    /** 上传者名下的全部披风；用于用户彻底删除时一并清理。 */
    fun findAllByUploaderId(uploaderId: Long): List<Cape>
}
