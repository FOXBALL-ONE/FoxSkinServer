package top.foxball.foxskinserver.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.Report

/** 纹理举报数据访问仓储。 */
interface ReportRepository : JpaRepository<Report, Long> {
    fun findAllByTextureIdAndStatus(textureId: Long, status: Int): List<Report>
    
    /** 管理端举报检索；[status] 与 [textureId] 传 null 表示不过滤。 */
    @Query(
        """
        select r from Report r
        where (:status is null or r.status = :status)
          and (:textureId is null or r.textureId = :textureId)
        """
    )
    fun search(
        @Param("status") status: Int?,
        @Param("textureId") textureId: Long?,
        pageable: Pageable,
    ): Page<Report>
    
    /** 指向这些材质的所有举报，用于材质被删除时一次性清理悬挂记录。 */
    fun findAllByTextureIdIn(textureIds: Collection<Long>): List<Report>
}
