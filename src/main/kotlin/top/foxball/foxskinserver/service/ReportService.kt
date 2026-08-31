package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Report
import top.foxball.foxskinserver.repository.ReportRepository

@Service
class ReportService(private val repository: ReportRepository) {
    fun getReportById(id: Long): Report? = repository.findById(id).orElse(null)
    fun getReportsByTextureIdAndStatus(textureId: Long, status: Int): List<Report> = repository.findAllByTextureIdAndStatus(textureId, status)
    fun save(report: Report): Report = repository.save(report)
    fun saveAll(reports: Iterable<Report>): List<Report> = repository.saveAll(reports)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
