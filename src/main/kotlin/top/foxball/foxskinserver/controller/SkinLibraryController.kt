package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.TextureService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime

/**
 * 皮肤库：站内公开材质，也是“我的衣柜”挑选材质的来源。
 *
 * @folder 用户/皮肤库
 */
@Validated
@RestController
@RequestMapping("/api/skinlib")
class SkinLibraryController(
    private val textureService: TextureService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 皮肤库列表
     * @param type 材质类型筛选，skin 皮肤、cape 披风，省略表示全部
     * @param keyword 材质名关键字
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listLibrary(
        @RequestParam("type", defaultValue = "") type: String,
        @RequestParam("keyword", defaultValue = "") keyword: String,
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "24") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class TextureData(
            val id: Long,
            val name: String,
            val type: String,
            val hash: String,
            val size: Long,
            @param:JsonProperty("uploader_id")
            val uploaderId: Long,
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
        
        if (type !in LIBRARY_TYPES) throw ParamErrorException("type 只能是 skin 或 cape")
        
        val paged = textureService.searchPublic(
            keyword.trim(),
            type,
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
     * @api 上传材质
     * @param file PNG 文件，必须是 64x64 或 64x32
     * @param name 材质名称
     * @param type 材质类型，steve、alex 或 cape
     * @param publicTexture 是否公开展示到皮肤库
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadTexture(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile,
        @RequestParam("name") name: String,
        @RequestParam("type", defaultValue = "steve") type: String,
        @RequestParam("public", defaultValue = "false") publicTexture: Boolean,
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
        )
        
        val texture = textureService.createFromUpload(principal.userId, name, type, publicTexture, file)
        val rs = TextureData(
            id = requireNotNull(texture.id),
            name = texture.name,
            type = texture.type,
            hash = texture.hash,
            size = texture.size,
            uploaderId = texture.uploaderId,
            publicTexture = texture.publicTexture,
            uploadAt = texture.uploadAt,
        )
        return builder.created()
            .data(rs)
            .build()
    }
    
    private companion object {
        /** 空串表示不筛选。 */
        val LIBRARY_TYPES = setOf("", "skin", "cape")
    }
}
