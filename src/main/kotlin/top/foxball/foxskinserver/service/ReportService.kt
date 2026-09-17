package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Report
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.repository.ReportRepository

@Service
class ReportService(private val repository: ReportRepository) {
    fun getReportById(id: Long): Report? = repository.findById(id).orElse(null)
    fun getReportsByTextureIdAndStatus(textureId: Long, status: Int): List<Report> =
        repository.findAllByTextureIdAndStatus(textureId, status)
    
    fun save(report: Report): Report = repository.save(report)
    fun saveAll(reports: Iterable<Report>): List<Report> = repository.saveAll(reports)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
    fun deleteAll(reports: Iterable<Report>) = repository.deleteAll(reports)
    
    /** 管理端举报列表。 */
    fun search(status: Int?, textureId: Long?, pageable: Pageable): Page<Report> =
        repository.search(status, textureId, pageable)
    
    /** 更新举报处理状态；目标不存在时返回 null。 */
    fun updateStatus(id: Long, status: Int): Report? {
        if (status !in VALID_STATUSES) {
            throw ParamErrorException("举报状态只能是 0（待处理）、1（已解决）或 2（已驳回）")
        }
        val report = repository.findById(id).orElse(null) ?: return null
        report.status = status
        return repository.save(report)
    }
    
    /** 清理指向这些材质的全部举报，用于材质被删除时避免留下悬挂记录。 */
    fun deleteByTextureIds(textureIds: Collection<Long>) {
        if (textureIds.isEmpty()) return
        repository.deleteAll(repository.findAllByTextureIdIn(textureIds))
    }
    
    private companion object {
        val VALID_STATUSES = setOf(Report.PENDING, Report.RESOLVED, Report.REJECTED)
    }
}
