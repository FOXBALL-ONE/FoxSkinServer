package top.foxball.foxskinserver.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import top.foxball.foxskinserver.config.YggdrasilProperties
import top.foxball.foxskinserver.handler.YggdrasilException
import top.foxball.foxskinserver.security.YggdrasilRateLimiter
import top.foxball.foxskinserver.service.YggdrasilService
import jakarta.servlet.http.HttpServletRequest
import java.util.*

/** Blessing Skin/authlib-injector 使用的 Yggdrasil 原生协议端点。 */
@RestController
@RequestMapping("\${shopmall.yggdrasil.api-path:/api/yggdrasil}")
class YggdrasilController(
    private val service: YggdrasilService,
    private val properties: YggdrasilProperties = YggdrasilProperties(),
    private val rateLimiter: YggdrasilRateLimiter? = null,
) {
    private val log = LoggerFactory.getLogger(YggdrasilController::class.java)

    @GetMapping("", "/")
    fun metadata(): Map<String, Any> {
        log.debug("Yggdrasil metadata 请求")
        return service.metadata()
    }

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
        log.info(
            "Yggdrasil authenticate 成功: profile_count={}, selected_profile={}",
            result.availableProfiles.size,
            result.selectedProfile?.get("name"),
        )
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
        result.selectedProfile?.let { response["selectedProfile"] = it }
        result.user?.let { response["user"] = it }
        log.info(
            "Yggdrasil refresh 成功: profile_count={}, selected_profile={}",
            result.availableProfiles.size,
            result.selectedProfile?.get("name"),
        )
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
        log.info("Yggdrasil validate 成功")
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
        log.info("Yggdrasil invalidate 成功")
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
        log.info("Yggdrasil signout 成功")
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/sessionserver/session/minecraft/join", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun join(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
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
            clientIp = request.remoteAddr,
        )
        log.info(
            "Yggdrasil join 成功: selected_profile={}, server_id={}",
            body["selectedProfile"],
            body["serverId"],
        )
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/sessionserver/session/minecraft/hasJoined")
    fun hasJoined(
        @RequestParam("username", required = false) username: String?,
        @RequestParam("serverId", required = false) serverId: String?,
        @RequestParam("ip", required = false) ip: String?,
    ): ResponseEntity<Any> {
        log.info(
            "Yggdrasil hasJoined 请求: username={}, server_id={}, ip_present={}",
            username,
            serverId,
            ip != null,
        )
        val profile = service.hasJoined(username, serverId, ip)
        if (profile == null) {
            log.info("Yggdrasil hasJoined 未匹配: username={}, server_id={}", username, serverId)
            return ResponseEntity.noContent().build()
        }
        log.info("Yggdrasil hasJoined 成功: username={}, server_id={}", username, serverId)
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/sessionserver/session/minecraft/profile/{uuid}")
    fun profile(
        @PathVariable("uuid") uuid: String,
        @RequestParam("unsigned", defaultValue = "true") unsigned: Boolean,
    ): ResponseEntity<Any> {
        log.debug("Yggdrasil profile 请求: uuid={}, unsigned={}", uuid, unsigned)
        val profile = service.profile(uuid, unsigned)
        if (profile == null) {
            log.info("Yggdrasil profile 未找到: uuid={}", uuid)
            return ResponseEntity.noContent().build()
        }
        return ResponseEntity.ok(profile)
    }

    @PostMapping("/api/profiles/minecraft", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun profiles(@RequestBody names: List<String>): List<Map<String, String>> {
        log.debug("Yggdrasil profiles 请求: name_count={}", names.size)
        return service.profiles(names)
    }

    @PutMapping(
        "/api/user/profile/{uuid}/{textureType}",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun uploadTexture(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable("uuid") uuid: String,
        @PathVariable("textureType") textureType: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam("model", required = false) model: String?,
    ): ResponseEntity<Void> {
        service.uploadTexture(authorization, uuid, textureType, file, model)
        log.info("Yggdrasil uploadTexture 成功: uuid={}, texture_type={}", uuid, textureType)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/api/user/profile/{uuid}/{textureType}")
    fun deleteTexture(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable("uuid") uuid: String,
        @PathVariable("textureType") textureType: String,
    ): ResponseEntity<Void> {
        service.deleteTexture(authorization, uuid, textureType)
        log.info("Yggdrasil deleteTexture 成功: uuid={}, texture_type={}", uuid, textureType)
        return ResponseEntity.noContent().build()
    }

}
