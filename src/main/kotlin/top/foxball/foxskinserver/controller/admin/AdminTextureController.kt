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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.ReportService
import top.foxball.foxskinserver.service.TextureService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 管理端皮肤接口，仅管理员可访问。披风走 [AdminCapeController]，不在此列出。
 *
 * @folder 管理端/皮肤
 */
@Validated
@RestController
@RequestMapping("/api/admin/textures")
@PreAuthorize("hasRole('ADMIN')")
class AdminTextureController(
    private val textureService: TextureService,
    private val reportService: ReportService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 皮肤列表
     * @param keyword 皮肤名称关键字，空串表示不过滤
     * @param uploaderId 上传者用户主键，省略表示不过滤
     * @param publicTexture 是否公开，省略表示不过滤
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listTextures(
        @RequestParam("keyword", defaultValue = "") keyword: String,
        @RequestParam("uploader_id", required = false) uploaderId: Long?,
        @RequestParam("public", required = false) publicTexture: Boolean?,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "25") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class TextureData(
            val id: Long,
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
            val list: List<TextureData>,
            val pagination: Pagination,
        )
        
        val paged = textureService.search(
            keyword.trim(),
            uploaderId,
            publicTexture,
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")),
        )
        val list = paged.content.map { texture ->
            TextureData(
                id = requireNotNull(texture.id),
                name = texture.name,
                type = texture.type,
                hash = texture.hash,
                size = texture.size,
                uploaderId = texture.uploaderId,
                publicTexture = texture.publicTexture,
                uploadAt = texture.uploadAt,
                likes = texture.likes,
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
     * @api 皮肤详情
     * @param textureId 皮肤主键
     */
    @GetMapping("/{texture_id}")
    fun getTexture(@PathVariable("texture_id") textureId: Long): ResponseEntity<Response> {
        data class TextureData(
            val id: Long,
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
        
        val texture = textureService.getSkinById(textureId)
            ?: return builder.notFound().build()
        val rs = TextureData(
            id = requireNotNull(texture.id),
            name = texture.name,
            type = texture.type,
            hash = texture.hash,
            size = texture.size,
            uploaderId = texture.uploaderId,
            publicTexture = texture.publicTexture,
            uploadAt = texture.uploadAt,
            likes = texture.likes,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 切换皮肤公开状态
     * @param textureId 皮肤主键
     * @param publicTexture 目标公开状态
     */
    @PatchMapping("/{texture_id}/public")
    fun updatePublic(
        @PathVariable("texture_id") textureId: Long,
        @RequestParam("public") publicTexture: Boolean,
    ): ResponseEntity<Response> {
        data class TextureData(
            val id: Long,
            val name: String,
            @param:JsonProperty("public")
            val publicTexture: Boolean,
        )
        
        val texture = textureService.setPublic(textureId, publicTexture)
            ?: return builder.notFound().build()
        val rs = TextureData(
            id = requireNotNull(texture.id),
            name = texture.name,
            publicTexture = texture.publicTexture,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 删除皮肤并清理其举报记录
     *
     * 先删举报再删材质：材质已被删除时举报会失去指向，反序则可能出现举报悬挂。
     * 底层的 file_metadata 记录保留，不做级联删除。
     *
     * @param textureId 皮肤主键
     */
    @DeleteMapping("/{texture_id}")
    fun deleteTexture(@PathVariable("texture_id") textureId: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val deleted: Boolean,
        )
        
        if (textureService.getSkinById(textureId) == null) return builder.notFound().build()
        reportService.deleteByTextureIds(listOf(textureId))
        textureService.deleteById(textureId)
        val rs = Response(textureId, true)
        return builder.ok()
            .data(rs)
            .build()
    }
}
