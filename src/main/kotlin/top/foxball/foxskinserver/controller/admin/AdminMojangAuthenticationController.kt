package top.foxball.foxskinserver.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.MojangAuthenticationState
import top.foxball.foxskinserver.service.MojangAuthenticationStateService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/** 正版混合认证运行时开关，仅管理员可查询和更新。 */
@RestController
@RequestMapping("/api/admin/mojang/status")
@PreAuthorize("hasRole('ADMIN')")
class AdminMojangAuthenticationController(
    private val service: MojangAuthenticationStateService,
    private val builder: ResponseBuilder,
) {
    @GetMapping
    fun status(): ResponseEntity<Response> {
        data class Response(
            val enabled: Boolean,
            val source: String,
            @param:JsonProperty("updated_by") val updatedBy: String,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )

        val state = service.get()
        val rs = Response(state.enabled, state.source, state.updatedBy, state.updatedAt)
        return builder.ok().data(rs).build()
    }

    @PutMapping
    fun update(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam("enabled") enabled: Boolean,
    ): ResponseEntity<Response> {
        data class Response(
            val enabled: Boolean,
            val source: String,
            @param:JsonProperty("updated_by") val updatedBy: String,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )

        val state = service.update(enabled, principal.email)
        val rs = Response(state.enabled, state.source, state.updatedBy, state.updatedAt)
        return builder.ok().data(rs).build()
    }
}