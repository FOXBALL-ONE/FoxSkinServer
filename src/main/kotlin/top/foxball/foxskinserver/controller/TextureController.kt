package top.foxball.foxskinserver.controller

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.YggdrasilService

/** Yggdrasil Profile 中 textures URL 对应的公开纹理下载端点。 */
@RestController
class TextureController(private val service: YggdrasilService) {
    @GetMapping("/textures/{hash}")
    fun texture(@PathVariable("hash") hash: String): ResponseEntity<Resource> {
        val texture = service.openTexture(hash) ?: return ResponseEntity.notFound().build()
        val contentType = texture.contentType?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
            ?: MediaType.APPLICATION_OCTET_STREAM
        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(texture.size)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .header("X-Content-Type-Options", "nosniff")
            .body(FileSystemResource(texture.path))
    }
}
