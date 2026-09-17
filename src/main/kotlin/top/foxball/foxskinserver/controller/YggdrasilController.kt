package top.foxball.foxskinserver.controller

import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.handler.YggdrasilException
import top.foxball.foxskinserver.config.YggdrasilProperties
import top.foxball.foxskinserver.security.YggdrasilRateLimiter
import top.foxball.foxskinserver.service.YggdrasilService
import java.util.LinkedHashMap
import java.util.Locale

/** Blessing Skin/authlib-injector 使用的 Yggdrasil 原生协议端点。 */
@RestController
@RequestMapping("\${shopmall.yggdrasil.api-path:/api/yggdrasil}")
class YggdrasilController(
    private val service: YggdrasilService,
    private val properties: YggdrasilProperties = YggdrasilProperties(),
    private val rateLimiter: YggdrasilRateLimiter? = null,
) {
    @GetMapping("", "/")
    fun metadata(): Map<String, Any> = service.metadata()
    
    @PostMapping("/authserver/authenticate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun authenticate(@RequestBody body: Map<String, Any?>): Map<String, Any> {
        if (body["username"] != null && body["username"] !is String ||
            body["password"] != null && body["password"] !is String ||
            body["clientToken"] != null && body["clientToken"] !is String
        ) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "username、password 和 clientToken 必须是字符串。",
            )
        }
        val identity = (body["username"] as? String)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val retryAfterMillis = rateLimiter?.retryAfterMillis(identity, properties.throttleIntervalMillis) ?: 0
        if (retryAfterMillis > 0) throw YggdrasilException(
            HttpStatus.FORBIDDEN,
            "ForbiddenOperationException",
            "请求过于频繁，请稍后重试。",
            retryAfterSeconds = (retryAfterMillis + 999) / 1000,
        )
        val selectedProfileBody = body["selectedProfile"]
        val selectedProfile = when {
            selectedProfileBody == null -> null
            selectedProfileBody !is Map<*, *> -> throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "selectedProfile 必须是对象。",
            )
            
            selectedProfileBody["id"] !is String || selectedProfileBody["id"].toString()
                .isBlank() -> throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "selectedProfile.id 必须是非空字符串。",
            )
            
            else -> selectedProfileBody["id"] as String
        }
        val requestUserBody = body["requestUser"]
        if (requestUserBody != null && requestUserBody !is Boolean) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "requestUser 必须是布尔值。",
            )
        }
        val result = service.authenticate(
            username = body["username"] as? String,
            password = body["password"] as? String,
            clientToken = body["clientToken"] as? String,
            selectedProfile = selectedProfile,
            requestUser = requestUserBody == true,
        )
        val response = LinkedHashMap<String, Any>()
        response["accessToken"] = result.accessToken
        response["clientToken"] = result.clientToken
        response["availableProfiles"] = result.availableProfiles
        result.selectedProfile?.let { response["selectedProfile"] = it }
        result.user?.let { response["user"] = it }
        return response
    }
    
    @PostMapping("/authserver/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(@RequestBody body: Map<String, Any?>): Map<String, Any> {
        if (body["accessToken"] != null && body["accessToken"] !is String ||
            body["clientToken"] != null && body["clientToken"] !is String
        ) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "accessToken 和 clientToken 必须是字符串。",
            )
        }
        val selectedProfileBody = body["selectedProfile"]
        val selectedProfile = when {
            selectedProfileBody == null -> null
            selectedProfileBody !is Map<*, *> -> throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "selectedProfile 必须是对象。",
            )
            
            selectedProfileBody["id"] !is String || selectedProfileBody["id"].toString()
                .isBlank() -> throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "selectedProfile.id 必须是非空字符串。",
            )
            
            else -> selectedProfileBody["id"] as String
        }
        val requestUserBody = body["requestUser"]
        if (requestUserBody != null && requestUserBody !is Boolean) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "requestUser 必须是布尔值。",
            )
        }
        val result = service.refresh(
            accessToken = body["accessToken"] as? String,
            clientToken = body["clientToken"] as? String,
            selectedProfile = selectedProfile,
            requestUser = requestUserBody == true,
        )
        val response = LinkedHashMap<String, Any>()
        response["accessToken"] = result.accessToken
        response["clientToken"] = result.clientToken
        response["availableProfiles"] = result.availableProfiles
        result.selectedProfile?.let { response["selectedProfile"] = it }
        result.user?.let { response["user"] = it }
        return response
    }
    
    @PostMapping("/authserver/validate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun validate(@RequestBody body: Map<String, Any?>): ResponseEntity<Void> {
        if (body["accessToken"] != null && body["accessToken"] !is String ||
            body["clientToken"] != null && body["clientToken"] !is String
        ) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "accessToken 和 clientToken 必须是字符串。",
            )
        }
        service.validate(body["accessToken"] as? String, body["clientToken"] as? String)
        return ResponseEntity.noContent().build()
    }
    
    @PostMapping("/authserver/invalidate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun invalidate(@RequestBody body: Map<String, Any?>): ResponseEntity<Void> {
        if (body["accessToken"] != null && body["accessToken"] !is String ||
            body["clientToken"] != null && body["clientToken"] !is String
        ) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "accessToken 和 clientToken 必须是字符串。",
            )
        }
        service.invalidate(body["accessToken"] as? String, body["clientToken"] as? String)
        return ResponseEntity.noContent().build()
    }
    
    @PostMapping("/authserver/signout", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun signout(@RequestBody body: Map<String, Any?>): ResponseEntity<Void> {
        if (body["username"] != null && body["username"] !is String ||
            body["password"] != null && body["password"] !is String
        ) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "username 和 password 必须是字符串。",
            )
        }
        val identity = (body["username"] as? String)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val retryAfterMillis = rateLimiter?.retryAfterMillis(identity, properties.throttleIntervalMillis) ?: 0
        if (retryAfterMillis > 0) throw YggdrasilException(
            HttpStatus.FORBIDDEN,
            "ForbiddenOperationException",
            "请求过于频繁，请稍后重试。",
            retryAfterSeconds = (retryAfterMillis + 999) / 1000,
        )
        service.signout(body["username"] as? String, body["password"] as? String)
        return ResponseEntity.noContent().build()
    }
    
    @PostMapping("/sessionserver/session/minecraft/join", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun join(@RequestBody body: Map<String, Any?>): ResponseEntity<Void> {
        if (body["accessToken"] != null && body["accessToken"] !is String ||
            body["selectedProfile"] != null && body["selectedProfile"] !is String ||
            body["serverId"] != null && body["serverId"] !is String
        ) {
            throw YggdrasilException(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "accessToken、selectedProfile 和 serverId 必须是字符串。",
            )
        }
        service.join(
            accessToken = body["accessToken"] as? String,
            selectedProfile = body["selectedProfile"] as? String,
            serverId = body["serverId"] as? String,
        )
        return ResponseEntity.noContent().build()
    }
    
    @GetMapping("/sessionserver/session/minecraft/hasJoined")
    fun hasJoined(
        @RequestParam("username", required = false) username: String?,
        @RequestParam("serverId", required = false) serverId: String?,
        @RequestParam("ip", required = false) ip: String?,
    ): ResponseEntity<Any> {
        val profile = service.hasJoined(username, serverId, ip) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(profile)
    }
    
    @GetMapping("/sessionserver/session/minecraft/profile/{uuid}")
    fun profile(
        @PathVariable("uuid") uuid: String,
        @RequestParam("unsigned", defaultValue = "true") unsigned: Boolean,
    ): ResponseEntity<Any> {
        val profile = service.profile(uuid, unsigned) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(profile)
    }
    
    @PostMapping("/api/profiles/minecraft", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun profiles(@RequestBody names: List<String>): List<Map<String, String>> = service.profiles(names)
    
}
