package top.foxball.foxskinserver.controller

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.FileService
import top.foxball.foxskinserver.service.UserService

/**
 * 公开头像端点。
 *
 * 头像是公开信息，用固定的 /avatar/{uid} 而不是签名链接：浏览器可按 URL 缓存，`<img>` 也无须带
 * Authorization 头。用户没设头像时返回 404，由前端回退到首字母头像。
 *
 * @folder 用户/头像
 */
@RestController
class AvatarController(
    private val userService: UserService,
    private val fileService: FileService,
) {
    @GetMapping("/avatar/{user_id}")
    fun avatar(@PathVariable("user_id") userId: Long): ResponseEntity<Resource> {
        val user = userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val fileId = user.avatarFileId ?: return ResponseEntity.notFound().build()
        val file = runCatching { fileService.openPublicFile(fileId) }.getOrNull()
            ?: return ResponseEntity.notFound().build()
        val contentType = file.contentType
            ?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
            ?: MediaType.APPLICATION_OCTET_STREAM
        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(file.sizeBytes)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(FileSystemResource(file.path))
    }
}
