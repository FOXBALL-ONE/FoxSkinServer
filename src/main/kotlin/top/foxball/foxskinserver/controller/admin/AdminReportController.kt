package top.foxball.foxskinserver.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.ReportService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 管理端举报审核接口，仅管理员可访问。
 *
 * @folder 管理端/举报
 */
@Validated
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
class AdminReportController(
    private val reportService: ReportService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 举报列表
     * @param status 举报状态过滤，0 待处理、1 已解决、2 已驳回；省略表示不过滤
     * @param textureId 被举报材质主键，省略表示不过滤
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listReports(
        @RequestParam("status", required = false) status: Int?,
        @RequestParam("texture_id", required = false) textureId: Long?,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "25") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class ReportData(
            val id: Long,
            @param:JsonProperty("texture_id")
            val textureId: Long,
            @param:JsonProperty("uploader_id")
            val uploaderId: Long,
            @param:JsonProperty("reporter_id")
            val reporterId: Long,
            val reason: String,
            val status: Int,
            @param:JsonProperty("report_at")
            val reportAt: LocalDateTime,
        )
        
        data class Pagination(
            val page: Int,
            val size: Int,
            val total: Long,
            @param:JsonProperty("total_pages")
            val totalPages: Int,
        )
        
        data class Response(
            val list: List<ReportData>,
            val pagination: Pagination,
        )
        
        val paged = reportService.search(
            status,
            textureId,
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")),
        )
        val list = paged.content.map { report ->
            ReportData(
                id = requireNotNull(report.id),
                textureId = report.textureId,
                uploaderId = report.uploaderId,
                reporterId = report.reporterId,
                reason = report.reason,
                status = report.status,
                reportAt = report.reportAt,
            )
        }
        val rs = Response(
            list,
            Pagination(paged.number + 1, paged.size, paged.totalElements, paged.totalPages),
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 举报详情
     * @param reportId 举报主键
     */
    @GetMapping("/{report_id}")
    fun getReport(@PathVariable("report_id") reportId: Long): ResponseEntity<Response> {
        data class ReportData(
            val id: Long,
            @param:JsonProperty("texture_id")
            val textureId: Long,
            @param:JsonProperty("uploader_id")
            val uploaderId: Long,
            @param:JsonProperty("reporter_id")
            val reporterId: Long,
            val reason: String,
            val status: Int,
            @param:JsonProperty("report_at")
            val reportAt: LocalDateTime,
        )
        
        val report = reportService.getReportById(reportId)
            ?: return builder.notFound().build()
        val rs = ReportData(
            id = requireNotNull(report.id),
            textureId = report.textureId,
            uploaderId = report.uploaderId,
            reporterId = report.reporterId,
            reason = report.reason,
            status = report.status,
            reportAt = report.reportAt,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 处理举报
     * @param reportId 举报主键
     * @param status 目标状态，0 待处理、1 已解决、2 已驳回
     */
    @PatchMapping("/{report_id}/status")
    fun updateStatus(
        @PathVariable("report_id") reportId: Long,
        @RequestParam("status") status: Int,
    ): ResponseEntity<Response> {
        data class ReportData(
            val id: Long,
            val status: Int,
        )
        
        val report = reportService.updateStatus(reportId, status)
            ?: return builder.notFound().build()
        val rs = ReportData(
            id = requireNotNull(report.id),
            status = report.status,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
}
