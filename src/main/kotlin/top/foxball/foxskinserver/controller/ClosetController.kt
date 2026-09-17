package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.UserClosetService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 我的衣柜：用户收藏进衣柜的材质，可自定义名称。
 *
 * @folder 用户/衣柜
 */
@Validated
@RestController
@RequestMapping("/api/users/me/closet")
class ClosetController(
    private val closetService: UserClosetService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 我的衣柜列表
     * @param type 材质类型筛选，skin 皮肤、cape 披风，省略表示全部
     * @param keyword 材质名或自定义名关键字
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listCloset(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam("type", defaultValue = "") type: String,
        @RequestParam("keyword", defaultValue = "") keyword: String,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "24") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class ClosetData(
            @param:JsonProperty("texture_id")
            val textureId: Long,
            @param:JsonProperty("item_name")
            val itemName: String?,
            val name: String,
            val type: String,
            val hash: String,
            val size: Long,
            @param:JsonProperty("uploader_id")
            val uploaderId: Long,
            @param:JsonProperty("public")
            val publicTexture: Boolean,
            @param:JsonProperty("upload_at")
            val uploadAt: LocalDateTime,
            val likes: Int,
        )
        
        data class Pagination(
            val page: Int,
            val size: Int,
            val total: Long,
            @param:JsonProperty("total_pages")
            val totalPages: Int,
        )
        
        data class Response(
            val list: List<ClosetData>,
            val pagination: Pagination,
        )
        
        if (type !in CLOSET_TYPES) throw ParamErrorException("type 只能是 skin 或 cape")
        
        val paged = closetService.searchCloset(
            principal.userId,
            type,
            keyword.trim(),
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id.textureId")),
        )
        val list = paged.content.map { entry ->
            ClosetData(
                textureId = entry.getTextureId(),
                itemName = entry.getItemName(),
                name = entry.getTextureName(),
                type = entry.getTextureType(),
                hash = entry.getTextureHash(),
                size = entry.getTextureSize(),
                uploaderId = entry.getTextureUploaderId(),
                publicTexture = entry.getTexturePublic(),
                uploadAt = entry.getTextureUploadAt(),
                likes = entry.getTextureLikes(),
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
     * @api 把材质收进衣柜
     * @param textureId 材质主键
     * @param itemName 衣柜中的自定义名称，省略则沿用材质名
     */
    @PostMapping
    fun addToCloset(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam("texture_id") textureId: Long,
        @RequestParam("item_name", required = false) itemName: String?,
    ): ResponseEntity<Response> {
        data class ClosetData(
            @param:JsonProperty("texture_id")
            val textureId: Long,
            @param:JsonProperty("item_name")
            val itemName: String?,
        )
        
        val entry = closetService.addToCloset(principal.userId, textureId, itemName)
        val rs = ClosetData(
            textureId = entry.id.textureId,
            itemName = entry.itemName,
        )
        return builder.created()
            .data(rs)
            .build()
    }
    
    /**
     * @api 重命名衣柜中的自定义名称
     * @param textureId 材质主键
     * @param itemName 新的自定义名称，传空串表示清掉自定义名
     */
    @PatchMapping("/{texture_id}")
    fun renameClosetEntry(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("texture_id") textureId: Long,
        @RequestParam("item_name", required = false) itemName: String?,
    ): ResponseEntity<Response> {
        data class ClosetData(
            @param:JsonProperty("texture_id")
            val textureId: Long,
            @param:JsonProperty("item_name")
            val itemName: String?,
        )
        
        if (!closetService.renameClosetEntry(principal.userId, textureId, itemName)) {
            return builder.notFound().build()
        }
        val rs = ClosetData(
            textureId = textureId,
            itemName = itemName?.trim()?.takeIf { it.isNotEmpty() },
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 把材质移出衣柜
     * @param textureId 材质主键
     */
    @DeleteMapping("/{texture_id}")
    fun removeFromCloset(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("texture_id") textureId: Long,
    ): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("texture_id")
            val textureId: Long,
            val removed: Boolean,
        )
        
        if (!closetService.removeFromCloset(principal.userId, textureId)) {
            return builder.notFound().build()
        }
        val rs = Response(textureId, true)
        return builder.ok()
            .data(rs)
            .build()
    }
    
    private companion object {
        /** 空串表示不筛选。 */
        val CLOSET_TYPES = setOf("", "skin", "cape")
    }
}
