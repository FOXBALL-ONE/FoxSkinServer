package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.MojangBindingService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/** 当前用户的 Minecraft 正版身份绑定。授权入口使用 /api/auth/oauth/microsoft/redirect。 */
@RestController
@RequestMapping("/api/account/mojang-bindings")
class MojangBindingController(
    private val service: MojangBindingService,
    private val builder: ResponseBuilder,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: AuthenticatedUser): ResponseEntity<Response> {
        data class BindingData(
            val id: Long,
            @param:JsonProperty("player_id") val playerId: Long,
            @param:JsonProperty("mojang_uuid") val mojangUuid: String,
            @param:JsonProperty("mojang_name") val mojangName: String,
            @param:JsonProperty("verified_at") val verifiedAt: LocalDateTime,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
        )
        val rs = service.list(principal.userId).map {
            BindingData(
                requireNotNull(it.id), it.playerId, it.mojangUuid.toString(), it.mojangName,
                it.verifiedAt, it.createdAt,
            )
        }
        return builder.ok().data(rs).build()
    }

    @DeleteMapping("/{binding_id}")
    fun unbind(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("binding_id") bindingId: Long,
    ): ResponseEntity<Response> {
        service.unbind(principal.userId, bindingId)
        return builder.ok().build()
    }
}
