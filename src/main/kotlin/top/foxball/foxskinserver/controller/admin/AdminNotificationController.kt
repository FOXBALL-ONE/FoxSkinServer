package top.foxball.foxskinserver.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.NotificationService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 管理端站内通知接口，仅管理员可访问。
 *
 * @folder 管理端/通知
 */
@Validated
@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
class AdminNotificationController(
    private val notificationService: NotificationService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 通知列表
     * @param notifiableType 被通知对象类型，空串表示不过滤
     * @param notifiableId 被通知对象主键，省略表示不过滤
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listNotifications(
        @RequestParam("notifiable_type", defaultValue = "") notifiableType: String,
        @RequestParam("notifiable_id", required = false) notifiableId: Long?,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "25") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class NotificationData(
            val id: String,
            val type: String,
            @param:JsonProperty("notifiable_type")
            val notifiableType: String,
            @param:JsonProperty("notifiable_id")
            val notifiableId: Long,
            val data: String,
            @param:JsonProperty("read_at")
            val readAt: LocalDateTime?,
            @param:JsonProperty("created_at")
            val createdAt: LocalDateTime?,
        )
        
        data class Pagination(
            val page: Int,
            val size: Int,
            val total: Long,
            @param:JsonProperty("total_pages")
            val totalPages: Int,
        )
        
        data class Response(
            val list: List<NotificationData>,
            val pagination: Pagination,
        )
        
        val paged = notificationService.search(
            notifiableType.trim(),
            notifiableId,
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        val list = paged.content.map { notification ->
            NotificationData(
                id = notification.id,
                type = notification.type,
                notifiableType = notification.notifiableType,
                notifiableId = notification.notifiableId,
                data = notification.data,
                readAt = notification.readAt,
                createdAt = notification.createdAt,
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
     * @api 下发站内通知
     * @param notifiableType 被通知对象类型
     * @param notifiableId 被通知对象主键
     * @param type 通知类型
     * @param data 通知载荷，通常为 JSON 文本
     */
    @PostMapping
    fun createNotification(
        @RequestParam("notifiable_type") notifiableType: String,
        @RequestParam("notifiable_id") notifiableId: Long,
        @RequestParam("type") type: String,
        @RequestParam("data", defaultValue = "{}") data: String,
    ): ResponseEntity<Response> {
        data class NotificationData(
            val id: String,
            val type: String,
            @param:JsonProperty("notifiable_type")
            val notifiableType: String,
            @param:JsonProperty("notifiable_id")
            val notifiableId: Long,
            val data: String,
            @param:JsonProperty("read_at")
            val readAt: LocalDateTime?,
            @param:JsonProperty("created_at")
            val createdAt: LocalDateTime?,
        )
        
        val notification = notificationService.create(
            notifiableType.trim(),
            notifiableId,
            type.trim(),
            data,
        )
        val rs = NotificationData(
            id = notification.id,
            type = notification.type,
            notifiableType = notification.notifiableType,
            notifiableId = notification.notifiableId,
            data = notification.data,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
        return builder.created()
            .data(rs)
            .build()
    }
    
    /**
     * @api 删除通知
     * @param notificationId 通知主键
     */
    @DeleteMapping("/{notification_id}")
    fun deleteNotification(@PathVariable("notification_id") notificationId: String): ResponseEntity<Response> {
        data class Response(
            val id: String,
            val deleted: Boolean,
        )
        
        notificationService.getNotificationById(notificationId)
            ?: return builder.notFound().build()
        notificationService.deleteById(notificationId)
        val rs = Response(notificationId, true)
        return builder.ok()
            .data(rs)
            .build()
    }
}
