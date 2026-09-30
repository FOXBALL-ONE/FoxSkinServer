package top.foxball.foxskinserver.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import top.foxball.foxskinserver.config.FileProperties
import top.foxball.foxskinserver.config.YggdrasilProperties
import top.foxball.foxskinserver.entity.jdbc.Player
import top.foxball.foxskinserver.entity.jdbc.Texture
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.handler.BusinessException
import top.foxball.foxskinserver.handler.YggdrasilException
import top.foxball.foxskinserver.repository.PlayerRepository
import top.foxball.foxskinserver.repository.TextureRepository
import top.foxball.foxskinserver.repository.UserRepository
import top.foxball.foxskinserver.security.YggdrasilTokenStore
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.imageio.ImageIO

/** 实现 Blessing Skin yggdrasil-api 兼容协议的业务服务。 */
@Service
class YggdrasilService(
    private val users: UserRepository,
    private val players: PlayerRepository,
    private val textures: TextureRepository,
    private val tokenStore: YggdrasilTokenStore,
    private val passwordEncoder: PasswordEncoder,
    private val properties: YggdrasilProperties,
    private val fileProperties: FileProperties,
    private val objectMapper: ObjectMapper,
    private val fileService: FileService? = null,
    private val mojangVerifier: MojangVerifier? = null,
    private val mojangBindingService: MojangBindingService? = null,
    private val mojangStateService: MojangAuthenticationStateService? = null,
) {
    private val log = LoggerFactory.getLogger(YggdrasilService::class.java)
    private val keyPair: KeyPair = loadKeyPair()
    private val encoder = Base64.getEncoder()

    init {
        log.info(
            "Yggdrasil service 初始化: api_path={}, require_verified={}, token_expire_seconds={}, refresh_expire_seconds={}, join_expire_seconds={}",
            properties.apiPath,
            properties.requireVerified,
            properties.tokenExpireSeconds,
            properties.refreshExpireSeconds,
            properties.joinExpireSeconds
        )
        require(properties.apiPath.startsWith("/") && !properties.apiPath.contains("..")) {
            "Yggdrasil api-path 必须是安全的绝对路径"
        }
        require(
            properties.apiPath.length > 1 && !properties.apiPath.endsWith('/') &&
                    !properties.apiPath.contains("//") && properties.apiPath.none(Char::isWhitespace)
        ) {
            "Yggdrasil api-path 不能是根路径、尾随斜杠或包含空白字符"
        }
        require(properties.tokenExpireSeconds > 0) { "Yggdrasil token-expire-seconds 必须大于 0" }
        require(properties.refreshExpireSeconds >= properties.tokenExpireSeconds) {
            "Yggdrasil refresh-expire-seconds 不能小于 token-expire-seconds"
        }
        require(properties.joinExpireSeconds > 0) { "Yggdrasil join-expire-seconds 必须大于 0" }
        require(properties.profileSearchMax in 2..100) { "Yggdrasil profile-search-max 必须在 2 到 100 之间" }
        require(properties.throttleIntervalMillis >= 0) { "Yggdrasil throttle-interval-millis 不能小于 0" }
    }

    /** 返回 authlib-injector 启动时读取的服务元数据。 */
    fun metadata(): Map<String, Any> = linkedMapOf(
        "meta" to linkedMapOf(
            "serverName" to properties.serverName,
            "implementationName" to properties.implementationName,
            "implementationVersion" to properties.implementationVersion,
            "feature.non_email_login" to false,
            "feature.legacy_skin_api" to false,
            "feature.no_mojang_namespace" to false,
            "feature.enable_mojang_anti_features" to false,
            "feature.enable_profile_key" to false,
            "feature.username_check" to false,
        ),
        "skinDomains" to skinDomains(),
        // Blessing Skin 直接返回 OpenSSL 的 PEM 公钥，authlib-injector 会据此验签材质。
        "signaturePublickey" to publicKeyPem(),
    )

    data class AuthenticationResult(
        val accessToken: String,
        val clientToken: String,
        val availableProfiles: List<Map<String, String>>,
        val selectedProfile: Map<String, String>?,
        val user: Map<String, Any>?,
    )

    fun authenticate(
        username: String?,
        password: String?,
        clientToken: String?,
        selectedProfile: String?,
        requestUser: Boolean = false,
    ): AuthenticationResult {
        log.debug(
            "Yggdrasil authenticate 业务开始: username_present={}, client_token_present={}, selected_profile_present={}, request_user={}",
            username != null,
            !clientToken.isNullOrBlank(),
            !selectedProfile.isNullOrBlank(),
            requestUser
        )
        if (username == null || password == null) throw malformed("username/password")
        val email = username.trim().lowercase(Locale.ROOT)
        if (email.length > MAX_EMAIL_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
            throw malformed("username/password")
        }
        if (clientToken != null && clientToken.length > MAX_CLIENT_TOKEN_LENGTH) {
            throw malformed("clientToken")
        }
        val user = users.findByEmail(email)
        if (user == null || user.permission == User.BANNED ||
            (properties.requireVerified && !user.verified) || password.isNullOrEmpty() ||
            !passwordEncoder.matches(password, user.password)
        ) {
            log.info("Yggdrasil authenticate 凭据校验失败: username={}", email)
            throw invalidCredentials()
        }
        val userId = user.id ?: throw invalidCredentials()
        val userProfiles = players.findAllByUserId(userId)
        val selected = selectedProfile?.takeIf { it.isNotBlank() }?.let { parseUuid(it) }
        if (selected != null && userProfiles.none { it.uuid == selected }) {
            throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "该角色不属于当前用户")
        }
        val selectedPlayer = selected?.let { id -> userProfiles.first { it.uuid == id } }
            ?: userProfiles.singleOrNull()
        val accessToken = randomToken()
        val now = System.currentTimeMillis() / 1000
        val token = YggdrasilTokenStore.Token(
            accessToken = accessToken,
            clientToken = clientToken?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            userId = userId,
            profileId = selectedPlayer?.uuid,
            issuedAt = now,
            expiresAt = now + properties.tokenExpireSeconds.coerceAtLeast(1),
            refreshExpiresAt = now + properties.refreshExpireSeconds.coerceAtLeast(1),
        )
        tokenStore.save(token)
        log.debug(
            "Yggdrasil authenticate 令牌已保存: user_id={}, profile_count={}, selected_profile={}",
            userId,
            userProfiles.size,
            selectedPlayer?.uuid
        )
        val profiles = userProfiles.map(::profileSummary)
        val userData = if (requestUser) linkedMapOf<String, Any>(
            "id" to userUuid(user.email),
            "properties" to emptyList<Any>(),
        ) else null
        return AuthenticationResult(
            token.accessToken,
            token.clientToken,
            profiles,
            selectedPlayer?.let(::profileSummary),
            userData,
        )
    }

    fun refresh(
        accessToken: String?,
        clientToken: String?,
        selectedProfile: String?,
        requestUser: Boolean = false,
    ): AuthenticationResult {
        log.debug(
            "Yggdrasil refresh 业务开始: access_token_present={}, client_token_present={}, selected_profile_present={}, request_user={}",
            !accessToken.isNullOrBlank(),
            !clientToken.isNullOrBlank(),
            !selectedProfile.isNullOrBlank(),
            requestUser
        )
        val old = token(accessToken)
        val now = System.currentTimeMillis() / 1000
        if (now >= old.refreshExpiresAt) throw expiredToken()
        if (!clientToken.isNullOrBlank() && old.clientToken != clientToken) throw invalidToken()
        val user = activeUser(old.userId)
        val userProfiles = players.findAllByUserId(user.id!!)
        val requestedProfileId = selectedProfile?.takeIf { it.isNotBlank() }?.let(::parseUuid)
        if (requestedProfileId != null && old.profileId != null) {
            throw protocolError(
                HttpStatus.BAD_REQUEST,
                "IllegalArgumentException",
                "Access token already has a profile assigned.",
            )
        }
        val profileId = requestedProfileId ?: old.profileId
        if (profileId != null && userProfiles.none { it.uuid == profileId }) {
            throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "该角色不属于当前用户")
        }
        val newAccessToken = randomToken()
        val replacement = old.copy(
            accessToken = newAccessToken,
            profileId = profileId,
            issuedAt = now,
            expiresAt = now + properties.tokenExpireSeconds.coerceAtLeast(1),
        )
        if (!tokenStore.rotate(old.accessToken, replacement)) {
            log.info("Yggdrasil refresh 令牌轮换失败: user_id={}", old.userId)
            throw invalidToken()
        }
        log.debug("Yggdrasil refresh 令牌已轮换: user_id={}, profile_id={}", old.userId, replacement.profileId)
        val selected = profileId?.let { id -> userProfiles.first { it.uuid == id } }
        return AuthenticationResult(
            replacement.accessToken,
            replacement.clientToken,
            userProfiles.map(::profileSummary),
            selected?.let(::profileSummary),
            if (requestUser) linkedMapOf<String, Any>(
                "id" to userUuid(user.email),
                "properties" to emptyList<Any>(),
            ) else null,
        )
    }

    fun validate(accessToken: String?, clientToken: String?) {
        log.debug(
            "Yggdrasil validate 业务开始: access_token_present={}, client_token_present={}",
            !accessToken.isNullOrBlank(),
            !clientToken.isNullOrBlank()
        )
        val token = token(accessToken)
        if (System.currentTimeMillis() / 1000 >= token.expiresAt) throw expiredToken()
        if (!clientToken.isNullOrBlank() && token.clientToken != clientToken) throw invalidToken()
        activeUser(token.userId)
    }

    fun invalidate(accessToken: String?, @Suppress("UNUSED_PARAMETER") clientToken: String?) {
        log.debug("Yggdrasil invalidate 业务开始: access_token_present={}", !accessToken.isNullOrBlank())
        // Blessing Skin 不校验 invalidate 的 clientToken，令牌不存在时同样返回 204。
        val token = accessToken?.takeIf { it.isNotBlank() }?.let(tokenStore::find) ?: return
        tokenStore.revoke(token.accessToken)
        log.debug("Yggdrasil invalidate 已撤销: user_id={}", token.userId)
    }

    fun signout(username: String?, password: String?) {
        log.debug("Yggdrasil signout 业务开始: username_present={}", username != null)
        if (username == null || password == null) throw malformed("username/password")
        val email = username.trim().lowercase(Locale.ROOT)
        if (email.length > MAX_EMAIL_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
            throw malformed("username/password")
        }
        val user = users.findByEmail(email)
        if (user == null || user.permission == User.BANNED ||
            (properties.requireVerified && !user.verified) || password.isNullOrEmpty() ||
            !passwordEncoder.matches(password, user.password)
        ) throw invalidCredentials()
        val userId = user.id ?: throw invalidCredentials()
        tokenStore.current(userId)?.let(tokenStore::revoke)
        log.debug("Yggdrasil signout 已完成: user_id={}", userId)
    }

    fun join(accessToken: String?, selectedProfile: String?, serverId: String?, clientIp: String? = null) {
        log.debug(
            "Yggdrasil join 业务开始: access_token_present={}, selected_profile={}, server_id_present={}",
            !accessToken.isNullOrBlank(),
            selectedProfile,
            !serverId.isNullOrBlank()
        )
        val profileId = selectedProfile?.takeIf { it.isNotBlank() }?.let(::parseUuid)
            ?: throw malformed("selectedProfile")
        // 现代 Minecraft 允许空 serverId（通常是空哈希），但字段本身仍必须存在。
        val id = serverId ?: throw malformed("serverId")
        if (id.length > MAX_SERVER_ID_LENGTH) throw malformed("serverId")
        val localToken = accessToken?.let(tokenStore::find)
        if (localToken != null) {
            if (System.currentTimeMillis() / 1000 >= localToken.expiresAt) throw expiredToken()
            val player = players.findByUuid(profileId) ?: throw invalidToken()
            if (player.userId != localToken.userId || localToken.profileId != profileId) {
                throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "角色与令牌不匹配")
            }
            activeUser(localToken.userId)
            tokenStore.saveJoin(id, localToken.accessToken, profileId, clientIp, properties.joinExpireSeconds)
        } else {
            try {
                mojangStateService?.requireEnabled() ?: throw invalidToken()
                val verifier = mojangVerifier ?: throw invalidToken()
                val bindingService = mojangBindingService ?: throw invalidToken()
                val official = verifier.verify(accessToken ?: throw invalidToken())
                if (official.id != profileId) {
                    throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "正版 Profile 与 selectedProfile 不匹配")
                }
                val binding = bindingService.refreshBindingForProfile(official)
                    ?: throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "该正版账号尚未绑定皮肤站角色")
                val player = players.findById(binding.playerId).orElse(null) ?: throw invalidToken()
                if (player.userId != binding.userId) {
                    throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "正版绑定角色无效")
                }
                activeUser(binding.userId)
                tokenStore.saveExternalJoin(id, player.uuid, official.name, binding.userId, clientIp, properties.joinExpireSeconds)
            } catch (exception: YggdrasilException) {
                throw exception
            } catch (_: BusinessException) {
                throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "正版账号校验失败")
            }
        }
        log.debug("Yggdrasil join 凭证已保存: profile_id={}, server_id={}", profileId, id)
    }

    /** 消费一次性 join 凭证，并返回带签名材质的 Profile；找不到时由控制器返回 204。 */
    fun hasJoined(
        username: String?,
        serverId: String?,
        @Suppress("UNUSED_PARAMETER") ip: String? = null
    ): Map<String, Any>? {
        log.debug(
            "Yggdrasil hasJoined 业务开始: username={}, server_id_present={}, ip_present={}",
            username,
            !serverId.isNullOrBlank(),
            !ip.isNullOrBlank()
        )
        // 与 Blessing Skin 当前实现保持一致：ip 参数暂只记录协议兼容性，不参与放行判断。
        val id = serverId ?: return null
        val credential = tokenStore.findJoin(id) ?: return null
        val profileId = credential.profileId
        val player = players.findByUuid(profileId) ?: return null
        if (username != (credential.username ?: player.name)) return null
        if (credential.external) {
            if (credential.userId != player.userId) return null
            activeUser(credential.userId)
        } else {
            val token = tokenStore.find(credential.accessToken) ?: return null
            if (System.currentTimeMillis() / 1000 >= token.expiresAt ||
                token.profileId != profileId || token.userId != player.userId
            ) return null
        }
        if (!credential.clientIp.isNullOrBlank() && !ip.isNullOrBlank() && credential.clientIp != ip) return null
        if (!tokenStore.consumeJoin(id, credential)) {
            log.info("Yggdrasil hasJoined 凭证消费失败: profile_id={}", profileId)
            return null
        }
        return profile(player, unsigned = false)
    }

    fun profile(uuid: String, unsigned: Boolean): Map<String, Any>? {
        log.debug("Yggdrasil profile 业务开始: uuid={}, unsigned={}", uuid, unsigned)
        val profileId = runCatching { parseUuid(uuid) }.getOrNull() ?: return null
        val player = players.findByUuid(profileId) ?: return null
        return profile(player, unsigned)
    }

    fun profiles(names: List<String>): List<Map<String, String>> {
        log.debug("Yggdrasil profiles 业务开始: name_count={}", names.size)
        val uniqueNames = names.distinct()
        if (names.size > properties.profileSearchMax) {
            throw protocolError(
                HttpStatus.FORBIDDEN,
                "ForbiddenOperationException",
                "一次最多查询 ${properties.profileSearchMax} 个角色",
            )
        }
        return uniqueNames
            .asSequence()
            .mapNotNull { name -> players.findByName(name) }
            .map(::profileSummary)
            .toList()
    }

    /** 按 Yggdrasil 材质上传协议校验令牌、清洗 PNG 并绑定到角色。 */
    @Transactional
    fun uploadTexture(
        authorization: String?,
        uuid: String,
        textureType: String,
        file: MultipartFile,
        model: String?,
    ) {
        val token = authorizedTextureToken(authorization)
        val profileId = parseUuid(uuid)
        val player = players.findByUuid(profileId) ?: throw invalidToken()
        if (player.userId != token.userId) {
            throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "Invalid token.")
        }
        if (textureType != "skin" && textureType != "cape") throw malformed("textureType")
        val normalizedModel = model.orEmpty()
        if (textureType == "skin" && normalizedModel !in setOf("", "slim")) throw malformed("model")
        if (textureType == "cape" && normalizedModel.isNotEmpty()) throw malformed("model")
        if (file.contentType != "image/png" || file.isEmpty || file.size > fileProperties.maxFileSizeBytes) {
            throw malformed("PNG file")
        }

        val image = file.inputStream.use { stream ->
            ImageIO.createImageInputStream(stream).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: throw malformed("PNG file")
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                val validDimensions = if (textureType == "skin") {
                    width in 64..2048 && height in 32..2048 && width % 64 == 0 && height % 32 == 0 && width.toLong() * height <= 4_194_304
                } else {
                    width in 22..2048 && height in 17..2048 &&
                        ((width % 64 == 0 && height % 32 == 0) || (width % 22 == 0 && height % 17 == 0)) &&
                        width.toLong() * height <= 4_194_304
                }
                    if (!validDimensions) throw malformed("texture dimensions")
                    reader.read(0) ?: throw malformed("PNG file")
                } catch (ex: YggdrasilException) {
                    throw ex
                } catch (_: Exception) {
                    throw malformed("PNG file")
                } finally {
                    reader.dispose()
                }
            }
        }
        val normalizedCapeWidth = if (textureType == "cape") ((image.width + 63) / 64) * 64 else image.width
        val normalizedCapeHeight = if (textureType == "cape") ((image.height + 31) / 32) * 32 else image.height
        val sanitizedImage = if (textureType == "cape" && (image.width != normalizedCapeWidth || image.height != normalizedCapeHeight)) {
            BufferedImage(normalizedCapeWidth, normalizedCapeHeight, BufferedImage.TYPE_INT_ARGB).also { canvas ->
                val graphics = canvas.createGraphics()
                try {
                    graphics.drawImage(image, 0, 0, null)
                } finally {
                    graphics.dispose()
                }
            }
        } else image
        val sanitizedBytes = ByteArrayOutputStream().use { output ->
            if (!ImageIO.write(sanitizedImage, "png", output)) throw malformed("PNG file")
            output.toByteArray()
        }
        val originalFilename = file.originalFilename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: "$textureType.png"
        val sanitizedFile = object : MultipartFile {
            override fun getName(): String = "file"
            override fun getOriginalFilename(): String = originalFilename
            override fun getContentType(): String = "image/png"
            override fun isEmpty(): Boolean = sanitizedBytes.isEmpty()
            override fun getSize(): Long = sanitizedBytes.size.toLong()
            override fun getBytes(): ByteArray = sanitizedBytes
            override fun getInputStream() = ByteArrayInputStream(sanitizedBytes)
            override fun transferTo(dest: File) = dest.writeBytes(sanitizedBytes)
        }
        val userId = token.userId
        val storedFile = requireNotNull(fileService) { "Yggdrasil 材质上传未配置文件服务" }
            .upload(userId, listOf(sanitizedFile)).single().file
        val displayName = originalFilename.substringBeforeLast('.', originalFilename).take(50).ifBlank { textureType }
        val texture = textures.save(
            Texture(
                file = storedFile,
                name = displayName,
                type = if (textureType == "cape") "cape" else if (normalizedModel == "slim") "alex" else "steve",
                hash = storedFile.sha256,
                size = storedFile.sizeBytes,
                uploaderId = userId,
                publicTexture = false,
            ),
        )
        if (textureType == "skin") player.skinTextureId = requireNotNull(texture.id) else player.capeTextureId =
            requireNotNull(texture.id)
        player.lastModified = java.time.LocalDateTime.now()
        players.save(player)
        log.info("Yggdrasil 材质上传完成: profile_id={}, texture_type={}", profileId, textureType)
    }

    /** 清除角色当前绑定的皮肤或披风。 */
    @Transactional
    fun deleteTexture(authorization: String?, uuid: String, textureType: String) {
        val token = authorizedTextureToken(authorization)
        val profileId = parseUuid(uuid)
        val player = players.findByUuid(profileId) ?: throw invalidToken()
        if (player.userId != token.userId) {
            throw protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "Invalid token.")
        }
        when (textureType) {
            "skin" -> player.skinTextureId = -1
            "cape" -> player.capeTextureId = 0
            else -> throw malformed("textureType")
        }
        player.lastModified = java.time.LocalDateTime.now()
        players.save(player)
        log.info("Yggdrasil 材质清除完成: profile_id={}, texture_type={}", profileId, textureType)
    }

    data class TextureDownload(val path: Path, val contentType: String?, val size: Long)

    /** 按纹理哈希提供公开图片，URL 会被写入 Profile 的 textures 属性。 */
    fun openTexture(hash: String): TextureDownload? {
        log.debug("Yggdrasil texture 业务开始: hash={}", hash)
        val normalizedHash = hash.trim().lowercase(Locale.ROOT)
        if (!normalizedHash.matches(TEXTURE_HASH_PATTERN)) return null
        val texture = textures.findByHash(normalizedHash) ?: return null
        val file = texture.file ?: return null
        if (file.storage != "local") return null
        val root = Paths.get(fileProperties.storagePath).toAbsolutePath().normalize()
        val path = root.resolve(file.relativePath).normalize()
        if (!path.startsWith(root) || !Files.isRegularFile(path)) return null
        // 规范化真实路径，防止存储目录中的符号链接把公开端点指向目录外文件。
        val realRoot = runCatching { root.toRealPath() }.getOrNull() ?: return null
        val realPath = runCatching { path.toRealPath() }.getOrNull() ?: return null
        if (!realPath.startsWith(realRoot)) return null
        val size = runCatching { Files.size(realPath) }.getOrNull() ?: return null
        return TextureDownload(realPath, file.contentType, size)
    }

    private fun profile(player: Player, unsigned: Boolean): Map<String, Any> {
        val id = compactUuid(player.uuid)
        val result = LinkedHashMap<String, Any>()
        result["id"] = id
        result["name"] = player.name
        val texturesJson = texturePayload(player, unsigned)
        val value = encoder.encodeToString(texturesJson.toByteArray(StandardCharsets.UTF_8))
        val property = LinkedHashMap<String, Any>()
        property["name"] = "textures"
        property["value"] = value
        if (!unsigned) property["signature"] = sign(value)
        result["properties"] = listOf(
            property,
            linkedMapOf<String, Any>("name" to "uploadableTextures", "value" to "skin,cape"),
        )
        return result
    }

    private fun texturePayload(player: Player, unsigned: Boolean): String {
        val texturesMap = LinkedHashMap<String, Any>()
        texture(player.skinTextureId)?.takeIf {
            it.hash.trim().lowercase(Locale.ROOT).matches(TEXTURE_HASH_PATTERN)
        }?.let { skin ->
            val skinData = LinkedHashMap<String, Any>()
            skinData["url"] = textureUrl(skin)
            if (skin.type.equals("alex", ignoreCase = true) || skin.type.equals("slim", ignoreCase = true)) {
                skinData["metadata"] = mapOf("model" to "slim")
            }
            texturesMap["SKIN"] = skinData
        }
        texture(player.capeTextureId)?.takeIf {
            it.hash.trim().lowercase(Locale.ROOT).matches(TEXTURE_HASH_PATTERN)
        }?.let { cape ->
            texturesMap["CAPE"] = mapOf("url" to textureUrl(cape))
        }
        val payload = linkedMapOf<String, Any>(
            "timestamp" to System.currentTimeMillis(),
            "profileId" to compactUuid(player.uuid),
            "profileName" to player.name,
            "isPublic" to true,
            "textures" to texturesMap,
        )
        if (!unsigned) payload["signatureRequired"] = true
        return objectMapper.writeValueAsString(payload)
    }

    private fun texture(id: Long): Texture? = if (id <= 0) null else textures.findByIdWithFile(id)

    private fun textureUrl(texture: Texture): String {
        val hash = texture.hash.trim().lowercase(Locale.ROOT)
        val base = (properties.textureBaseUrl.takeIf { it.isNotBlank() } ?: fileProperties.baseUrl).trimEnd('/')
        return "$base/textures/$hash"
    }

    private fun profileSummary(player: Player): Map<String, String> = linkedMapOf(
        "id" to compactUuid(player.uuid),
        "name" to player.name,
    )

    private fun activeUser(userId: Long): User {
        val user = users.findUserById(userId)
        if (user == null || user.permission == User.BANNED) throw invalidToken()
        return user
    }

    private fun authorizedTextureToken(authorization: String?): YggdrasilTokenStore.Token {
        val value = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.takeIf { it.isNotBlank() }
            ?: throw YggdrasilException(
                HttpStatus.UNAUTHORIZED,
                "UnauthorizedOperationException",
                "Missing or invalid access token.",
            )
        val current = tokenStore.find(value)
            ?: throw YggdrasilException(
                HttpStatus.UNAUTHORIZED,
                "UnauthorizedOperationException",
                "Invalid access token."
            )
        if (System.currentTimeMillis() / 1000 >= current.expiresAt) {
            throw YggdrasilException(HttpStatus.UNAUTHORIZED, "UnauthorizedOperationException", "Invalid access token.")
        }
        try {
            activeUser(current.userId)
        } catch (_: YggdrasilException) {
            throw YggdrasilException(HttpStatus.UNAUTHORIZED, "UnauthorizedOperationException", "Invalid access token.")
        }
        return current
    }

    private fun token(accessToken: String?): YggdrasilTokenStore.Token {
        val value = accessToken?.takeIf { it.isNotBlank() } ?: throw invalidToken()
        return tokenStore.find(value) ?: throw invalidToken()
    }

    private fun parseUuid(value: String): UUID = runCatching {
        val normalized = value.replace("-", "")
        require(normalized.length == 32)
        UUID.fromString(
            normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-" +
                    normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-" + normalized.substring(20),
        )
    }.getOrElse { throw malformed("profile UUID") }

    private fun compactUuid(uuid: UUID): String = uuid.toString().replace("-", "")

    private fun publicKeyPem(): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray(StandardCharsets.US_ASCII))
            .encodeToString(keyPair.public.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    /** 合并配置白名单与材质基础地址的主机名，避免客户端因 skinDomains 缺少当前域名而拒绝材质。 */
    private fun skinDomains(): List<String> {
        val configured = listOf(".minecraft.net", ".mojang.com") + properties.skinDomains.flatMap { it.split(',') }
        val urlHosts = listOf(properties.textureBaseUrl, fileProperties.baseUrl)
            .mapNotNull { value -> runCatching { URI(value).host }.getOrNull() }
        return (configured + urlHosts)
            .map { domain ->
                val value = domain.trim()
                if (value.isEmpty()) return@map ""
                runCatching {
                    val candidate = if (value.contains("://")) value else "https://$value"
                    URI(candidate).host ?: value.substringBefore('/').substringBefore(':')
                }.getOrDefault(value.substringBefore('/').substringBefore(':'))
            }
            .filter(String::isNotEmpty)
            .map { it.lowercase(Locale.ROOT) }
            .distinct()
    }

    /** Blessing Skin 在 requestUser 响应中以邮箱和 DNS 命名空间生成稳定 UUID v5。 */
    private fun userUuid(email: String): String {
        val namespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        val namespaceBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        digest.update(namespaceBytes)
        digest.update(email.lowercase().toByteArray(StandardCharsets.UTF_8))
        val bytes = digest.digest().copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val binary = ByteBuffer.wrap(bytes)
        return compactUuid(UUID(binary.long, binary.long))
    }

    private fun randomToken(): String = UUID.randomUUID().toString().replace("-", "")

    private fun sign(value: String): String = Signature.getInstance("SHA1withRSA").run {
        initSign(keyPair.private)
        update(value.toByteArray(StandardCharsets.UTF_8))
        encoder.encodeToString(sign())
    }

    private fun invalidCredentials() = protocolError(
        HttpStatus.FORBIDDEN,
        "ForbiddenOperationException",
        "Invalid credentials. Invalid username or password."
    )

    private fun invalidToken() = protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "Invalid token.")
    private fun expiredToken() =
        protocolError(HttpStatus.FORBIDDEN, "ForbiddenOperationException", "Token has expired.")

    private fun malformed(field: String) =
        protocolError(HttpStatus.BAD_REQUEST, "IllegalArgumentException", "Missing or invalid $field.")

    private fun protocolError(status: HttpStatus, error: String, message: String): YggdrasilException =
        YggdrasilException(status, error, message)

    private fun loadKeyPair(): KeyPair {
        // 容器环境变量常把 PEM 换行写成字面量 \n，这里统一还原后再解析。
        val configured = properties.privateKeyPem.trim().replace("\\n", "\n")
        if (configured.isBlank()) {
            // 开发环境未配置密钥时使用临时密钥；生产必须注入固定的至少 4096 位 RSA 私钥。
            return KeyPairGenerator.getInstance("RSA").apply { initialize(4096) }.generateKeyPair()
        }
        val der = Base64.getMimeDecoder().decode(
            configured.replace(Regex("-----BEGIN [^-]+-----"), "")
                .replace(Regex("-----END [^-]+-----"), "")
                .replace(Regex("\\s"), ""),
        )
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = runCatching {
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(der))
        }.getOrElse {
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(wrapPkcs1(der)))
        }
        val publicKey = when (privateKey) {
            is java.security.interfaces.RSAPrivateCrtKey -> java.security.spec.RSAPublicKeySpec(
                privateKey.publicExponent,
                privateKey.modulus
            )

            else -> throw IllegalArgumentException("RSA 私钥必须包含公钥指数")
        }
        require((privateKey as java.security.interfaces.RSAPrivateKey).modulus.bitLength() >= 4096) {
            "Yggdrasil RSA 私钥长度必须至少为 4096 位"
        }
        return KeyPair(keyFactory.generatePublic(publicKey), privateKey)
    }

    /** 将 PKCS#1 RSAPrivateKey DER 包裹为 PKCS#8，避免引入额外 ASN.1 依赖。 */
    private fun wrapPkcs1(pkcs1: ByteArray): ByteArray {
        val algorithm = byteArrayOf(
            0x30,
            0x0d,
            0x06,
            0x09,
            0x2a,
            0x86.toByte(),
            0x48,
            0x86.toByte(),
            0xf7.toByte(),
            0x0d,
            0x01,
            0x01,
            0x01,
            0x05,
            0x00
        )
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val body = version + algorithm + byteArrayOf(0x04) + derLength(pkcs1.size) + pkcs1
        return byteArrayOf(0x30) + derLength(body.size) + body
    }

    private fun derLength(length: Int): ByteArray = when {
        length < 128 -> byteArrayOf(length.toByte())
        length <= 0xff -> byteArrayOf(0x81.toByte(), length.toByte())
        length <= 0xffff -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        else -> byteArrayOf(0x83.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte())
    }

    private companion object {
        /** 纹理摘要通常为 SHA-256，也兼容历史系统中的 32~128 位十六进制摘要。 */
        val TEXTURE_HASH_PATTERN = Regex("[0-9a-f]{32,128}")
        const val MAX_PASSWORD_LENGTH = 4096
        const val MAX_EMAIL_LENGTH = 254
        const val MAX_CLIENT_TOKEN_LENGTH = 256
        const val MAX_SERVER_ID_LENGTH = 256
    }

}
