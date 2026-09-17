package top.foxball.foxskinserver.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.UserService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 管理端用户接口，仅管理员可访问。
 *
 * @folder 管理端/用户
 */
@Validated
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(
    private val userService: UserService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 用户列表
     * @param keyword 用户名/邮箱/昵称关键字，空串表示不过滤
     * @param permission 权限等级过滤，省略表示不过滤
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listUsers(
        @RequestParam("keyword", defaultValue = "") keyword: String,
        @RequestParam("permission", required = false) permission: Int?,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "25") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val nickname: String,
            val permission: Int,
            val score: Int,
            val verified: Boolean,
            val ip: String,
            @param:JsonProperty("last_sign_at")
            val lastSignAt: LocalDateTime,
            @param:JsonProperty("register_at")
            val registerAt: LocalDateTime,
        )
        
        data class Pagination(
            val page: Int,
            val size: Int,
            val total: Long,
            @param:JsonProperty("total_pages")
            val totalPages: Int,
        )
        
        data class Response(
            val list: List<UserData>,
            val pagination: Pagination,
        )
        
        val paged = userService.search(
            keyword.trim(),
            permission,
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")),
        )
        val list = paged.content.map { user ->
            UserData(
                id = requireNotNull(user.id),
                username = user.username,
                email = user.email,
                nickname = user.nickname,
                permission = user.permission,
                score = user.score,
                verified = user.verified,
                ip = user.ip,
                lastSignAt = user.lastSignAt,
                registerAt = user.registerAt,
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
     * @api 用户详情
     * @param userId 用户主键
     */
    @GetMapping("/{user_id}")
    fun getUser(@PathVariable("user_id") userId: Long): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val nickname: String,
            val permission: Int,
            val score: Int,
            val verified: Boolean,
            val ip: String,
            @param:JsonProperty("last_sign_at")
            val lastSignAt: LocalDateTime,
            @param:JsonProperty("register_at")
            val registerAt: LocalDateTime,
        )
        
        val user = userService.findById(userId)
            ?: return builder.notFound().build()
        val rs = UserData(
            id = requireNotNull(user.id),
            username = user.username,
            email = user.email,
            nickname = user.nickname,
            permission = user.permission,
            score = user.score,
            verified = user.verified,
            ip = user.ip,
            lastSignAt = user.lastSignAt,
            registerAt = user.registerAt,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 调整用户权限等级
     * @param userId 目标用户主键
     * @param permission 目标权限等级，-1 封禁、0 普通、1 管理员、2 超级管理员
     */
    @PatchMapping("/{user_id}/permission")
    fun updatePermission(
        @AuthenticationPrincipal operator: AuthenticatedUser,
        @PathVariable("user_id") userId: Long,
        @RequestParam("permission") permission: Int,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val permission: Int,
        )
        
        val user = userService.updatePermission(userId, permission, operator.userId, operator.permission)
        val rs = UserData(
            id = requireNotNull(user.id),
            username = user.username,
            permission = user.permission,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 彻底删除用户及其名下数据
     * @param userId 目标用户主键
     */
    @DeleteMapping("/{user_id}")
    fun deleteUser(
        @AuthenticationPrincipal operator: AuthenticatedUser,
        @PathVariable("user_id") userId: Long,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val deleted: Boolean,
        )
        
        userService.deleteUserCompletely(userId, operator.userId, operator.permission)
        val rs = Response(userId, true)
        return builder.ok()
            .data(rs)
            .build()
    }
}
