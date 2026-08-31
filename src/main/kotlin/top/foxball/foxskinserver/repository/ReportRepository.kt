package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.Report

/** 纹理举报数据访问仓储。 */
interface ReportRepository : JpaRepository<Report, Long> {
    fun findAllByTextureIdAndStatus(textureId: Long, status: Int): List<Report>
}
