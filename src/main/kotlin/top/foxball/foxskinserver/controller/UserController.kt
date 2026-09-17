package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.service.AuthService
import top.foxball.foxskinserver.service.PlayerService
import top.foxball.foxskinserver.service.UserService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.time.LocalDateTime
import java.util.UUID

@RestController
class UserController(
    private val userService: UserService,
    private val playerService: PlayerService,
    private val authService: AuthService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/api/users/{id}")
    @PreAuthorize("#id == authentication.principal.userId or hasRole('ADMIN')")
    fun getUser(@PathVariable("id") id: Long): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String
        )
        
        val user = userService.findById(id)
            ?: return responseBuilder.notFound().build()
        val rs = UserData(
            requireNotNull(user.id),
            user.username,
            user.email
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    @GetMapping("/api/users/by-player")
    @PreAuthorize("hasRole('ADMIN')")
    fun getUserByPlayerName(
        @RequestParam("player_name") playerName: String,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
        )
        
        val player = playerService.getPlayerByName(playerName)
            ?: return responseBuilder.notFound().build()
        val user = userService.findById(player.userId)
            ?: return responseBuilder.notFound().build()
        val rs = UserData(
            requireNotNull(user.id),
            user.username,
            user.email,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    /**
     * @api 获取当前登录用户的个人资料
     */
    @GetMapping("/api/users/me")
    fun getMyProfile(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Response> {
        data class UserData(
            @param:JsonProperty("user_id")
            val userId: Long,
            val email: String,
            val username: String,
            val nickname: String,
            val locale: String?,
            @param:JsonProperty("avatar_file_id")
            val avatarFileId: UUID?,
            @param:JsonProperty("avatar_url")
            val avatarUrl: String?,
            val score: Int,
            val permission: Int,
            val verified: Boolean,
            @param:JsonProperty("is_dark_mode")
            val isDarkMode: Boolean,
            val ip: String,
            @param:JsonProperty("last_sign_at")
            val lastSignAt: LocalDateTime,
            @param:JsonProperty("register_at")
            val registerAt: LocalDateTime,
        )
        
        val user = userService.findById(principal.userId)
            ?: return responseBuilder.notFound().build()
        val rs = UserData(
            userId = requireNotNull(user.id),
            email = user.email,
            username = user.username,
            nickname = user.nickname,
            locale = user.locale,
            avatarFileId = user.avatarFileId,
            avatarUrl = user.avatarFileId?.let { "/avatar/${requireNotNull(user.id)}" },
            score = user.score,
            permission = user.permission,
            verified = user.verified,
            isDarkMode = user.isDarkMode,
            ip = user.ip,
            lastSignAt = user.lastSignAt,
            registerAt = user.registerAt,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    /**
     * @api 更新当前登录用户的个人资料
     * @param nickname 昵称，省略表示不修改
     * @param locale 界面语言，传空串表示回退站点默认语言
     * @param isDarkMode 是否启用深色模式，省略表示不修改
     */
    @PatchMapping("/api/users/me")
    fun updateMyProfile(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam("nickname", required = false) nickname: String?,
        @RequestParam("locale", required = false) locale: String?,
        @RequestParam("is_dark_mode", required = false) isDarkMode: Boolean?,
    ): ResponseEntity<Response> {
        data class UserData(
            @param:JsonProperty("user_id")
            val userId: Long,
            val email: String,
            val username: String,
            val nickname: String,
            val locale: String?,
            @param:JsonProperty("avatar_file_id")
            val avatarFileId: UUID?,
            @param:JsonProperty("avatar_url")
            val avatarUrl: String?,
            val score: Int,
            val permission: Int,
            val verified: Boolean,
            @param:JsonProperty("is_dark_mode")
            val isDarkMode: Boolean,
            val ip: String,
            @param:JsonProperty("last_sign_at")
            val lastSignAt: LocalDateTime,
            @param:JsonProperty("register_at")
            val registerAt: LocalDateTime,
        )
        
        val user = userService.updateProfile(principal.userId, nickname, locale, isDarkMode)
        val rs = UserData(
            userId = requireNotNull(user.id),
            email = user.email,
            username = user.username,
            nickname = user.nickname,
            locale = user.locale,
            avatarFileId = user.avatarFileId,
            avatarUrl = user.avatarFileId?.let { "/avatar/${requireNotNull(user.id)}" },
            score = user.score,
            permission = user.permission,
            verified = user.verified,
            isDarkMode = user.isDarkMode,
            ip = user.ip,
            lastSignAt = user.lastSignAt,
            registerAt = user.registerAt,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    /**
     * @api 修改当前登录用户的密码
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     */
    @PostMapping("/api/users/me/password")
    fun changeMyPassword(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam("current_password") currentPassword: String,
        @RequestParam("new_password") newPassword: String,
    ): ResponseEntity<Response> {
        data class Response(
            val changed: Boolean,
        )
        
        authService.changePassword(principal.userId, currentPassword, newPassword)
        val rs = Response(true)
        return responseBuilder.ok().data(rs).build()
    }
    
    /**
     * @api 上传并替换当前用户头像
     * @param file 图片文件，需为可解码格式，边长 16-1024 像素且不超过 2MB
     */
    @PostMapping("/api/users/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<Response> {
        data class AvatarData(
            @param:JsonProperty("avatar_file_id")
            val avatarFileId: UUID,
            @param:JsonProperty("avatar_url")
            val avatarUrl: String,
        )
        
        val user = userService.replaceAvatar(principal.userId, file)
        val rs = AvatarData(
            avatarFileId = requireNotNull(user.avatarFileId),
            avatarUrl = "/avatar/${requireNotNull(user.id)}",
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    /**
     * @api 移除当前用户头像
     */
    @DeleteMapping("/api/users/me/avatar")
    fun deleteAvatar(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Response> {
        data class Response(
            val removed: Boolean,
        )
        
        if (!userService.removeAvatar(principal.userId)) return responseBuilder.notFound().build()
        val rs = Response(true)
        return responseBuilder.ok().data(rs).build()
    }
}
