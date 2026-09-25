package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.handler.ForbiddenException
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.UserAlreadyExistsException
import top.foxball.foxskinserver.handler.UserNotFoundException
import top.foxball.foxskinserver.repository.UserRepository
import javax.imageio.ImageIO

@Service
class UserService(
    private var userRepository: UserRepository,
    private val playerService: PlayerService,
    private val userClosetService: UserClosetService,
    private val textureService: TextureService,
    private val capeService: CapeService,
    private val reportService: ReportService,
    private val fileService: FileService,
    private val passwordEncoder: PasswordEncoder,
) {
    
    fun getUserById(id: Long): User? {
        return userRepository.findUserById(id)
    }
    
    fun findById(id: Long): User? = getUserById(id)
    
    fun save(user: User): User {
        return userRepository.save(user)
    }
    
    fun saveAll(users: Iterable<User>): List<User> = userRepository.saveAll(users)
    
    fun deleteById(id: Long) = userRepository.deleteById(id)
    
    fun deleteAllById(ids: Iterable<Long>) = userRepository.deleteAllById(ids)
    
    /**
     * 开放注册：校验邮箱/用户名规则与唯一性后落库，账号保持未验证状态，邮箱验证流程另行完成。
     * 注册成功不直接发验证邮件；站点是否要求验证由 YGGDRASIL_REQUIRE_VERIFIED 等开关决定。
     */
    fun register(email: String, username: String, password: String, nickname: String?): User {
        val normalizedEmail = email.trim().lowercase()
        if (!EMAIL_PATTERN.matches(normalizedEmail)) throw ParamErrorException("邮箱格式不正确")
        val normalizedUsername = username.trim()
        if (!USERNAME_PATTERN.matches(normalizedUsername)) {
            throw ParamErrorException("用户名需为 2-50 位字母、数字或下划线")
        }
        if (password.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            throw ParamErrorException("密码长度需在 $PASSWORD_MIN_LENGTH-$PASSWORD_MAX_LENGTH 个字符之间")
        }
        if (password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES) {
            throw ParamErrorException("密码过长，请改用更短的密码")
        }
        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw UserAlreadyExistsException("该邮箱已被注册")
        }
        if (userRepository.findByUsername(normalizedUsername) != null) {
            throw UserAlreadyExistsException("该用户名已被使用")
        }
        val trimmedNickname = nickname?.trim().orEmpty()
        if (trimmedNickname.length > NICKNAME_MAX_LENGTH) {
            throw ParamErrorException("昵称长度不能超过 $NICKNAME_MAX_LENGTH 个字符")
        }
        val user = User(
            email = normalizedEmail,
            password = requireNotNull(passwordEncoder.encode(password)) { "密码加密失败" },
            username = normalizedUsername,
            nickname = trimmedNickname.ifBlank { normalizedUsername },
            verified = false,
            verificationToken = "",
        )
        return userRepository.save(user)
    }
    
    /** 管理端用户列表：按关键字（用户名/邮箱/昵称）与权限等级检索。 */
    fun search(keyword: String, permission: Int?, pageable: Pageable): Page<User> =
        userRepository.search(keyword, permission, pageable)
    
    /**
     * 更新当前用户自己的资料。
     *
     * [nickname]、[locale]、[isDarkMode] 传 null 表示保持原值；昵称对应非空列，空白会被拒绝。
     * 登录用的邮箱与用户名不在此开放修改——它们带唯一约束且牵涉邮箱验证流程。
     */
    fun updateProfile(userId: Long, nickname: String?, locale: String?, isDarkMode: Boolean?): User {
        val user = userRepository.findUserById(userId) ?: throw UserNotFoundException()
        nickname?.let {
            val trimmed = it.trim()
            if (trimmed.isEmpty()) throw ParamErrorException("昵称不能为空")
            if (trimmed.length > NICKNAME_MAX_LENGTH) {
                throw ParamErrorException("昵称长度不能超过 $NICKNAME_MAX_LENGTH 个字符")
            }
            user.nickname = trimmed
        }
        locale?.let { user.locale = it.trim().takeIf { value -> value.isNotEmpty() } }
        isDarkMode?.let { user.isDarkMode = it }
        return userRepository.save(user)
    }
    
    /**
     * 上传并替换头像。
     *
     * 先校验是可解码的图片并限制尺寸，再交给 [FileService] 落盘；新头像写成功后才清理旧文件，
     * 中途失败时原头像仍然可用。返回的 [User.avatarFileId] 即 file_metadata 的主键。
     */
    fun replaceAvatar(userId: Long, file: MultipartFile): User {
        if (file.isEmpty) throw ParamErrorException("请选择一张图片文件")
        if (file.size > AVATAR_MAX_BYTES) {
            throw ParamErrorException("头像不能超过 ${AVATAR_MAX_BYTES / 1024 / 1024} MB")
        }
        val image = file.inputStream.use { ImageIO.read(it) }
            ?: throw ParamErrorException("文件不是可识别的图片")
        if (image.width !in AVATAR_MIN_SIDE..AVATAR_MAX_SIDE || image.height !in AVATAR_MIN_SIDE..AVATAR_MAX_SIDE) {
            throw ParamErrorException("头像边长需在 $AVATAR_MIN_SIDE-$AVATAR_MAX_SIDE 像素之间")
        }
        
        val user = userRepository.findUserById(userId) ?: throw UserNotFoundException()
        val previous = user.avatarFileId
        user.avatarFileId = fileService.upload(userId, listOf(file)).single().file.id
        val saved = userRepository.save(user)
        // 旧头像清理失败不应让整个替换失败，只是留下一份可回收的孤儿文件。
        previous?.let { runCatching { fileService.delete(userId, it) } }
        return saved
    }
    
    /** 清除头像并删除其文件；本来就没有头像时返回 false。 */
    fun removeAvatar(userId: Long): Boolean {
        val user = userRepository.findUserById(userId) ?: throw UserNotFoundException()
        val previous = user.avatarFileId ?: return false
        user.avatarFileId = null
        userRepository.save(user)
        runCatching { fileService.delete(userId, previous) }
        return true
    }
    
    /**
     * 调整目标用户权限等级。
     *
     * 只允许操作权限严格低于自己的账号，且只能授予严格低于自己的等级，
     * 避免管理员互相提权，或把上级封禁后把自己锁在门外。
     */
    fun updatePermission(targetUserId: Long, permission: Int, operatorId: Long, operatorPermission: Int): User {
        if (permission !in VALID_PERMISSIONS) {
            throw ParamErrorException("权限等级只能是 -1（封禁）、0（普通）、1（管理员）或 2（超级管理员）")
        }
        val target = userRepository.findUserById(targetUserId) ?: throw UserNotFoundException()
        requireOperatorOutranksTarget(target, operatorId, operatorPermission)
        if (permission >= operatorPermission) {
            throw ForbiddenException("不能授予不低于自己的权限等级")
        }
        target.permission = permission
        return userRepository.save(target)
    }
    
    /**
     * 彻底删除用户及其名下数据：角色、衣柜关联、上传的皮肤与披风、这些材质上的举报、以及文件。
     *
     * 顺序上先删角色再删材质，避免角色继续引用已删除的皮肤；整个操作在一个事务内完成。
     */
    @Transactional
    fun deleteUserCompletely(targetUserId: Long, operatorId: Long, operatorPermission: Int) {
        val target = userRepository.findUserById(targetUserId) ?: throw UserNotFoundException()
        requireOperatorOutranksTarget(target, operatorId, operatorPermission)
        
        val skins = textureService.getSkinsByUploader(targetUserId)
        val capes = capeService.getCapesByUploader(targetUserId)
        reportService.deleteByTextureIds(skins.mapNotNull { it.id } + capes.mapNotNull { it.id })
        
        playerService.deleteAll(playerService.getPlayersByUserId(targetUserId))
        userClosetService.deleteAll(userClosetService.getUserClosetsByUserId(targetUserId))
        textureService.deleteAll(skins)
        capeService.deleteAll(capes)
        fileService.deleteAllByOwnerId(targetUserId)
        
        userRepository.delete(target)
    }
    
    private fun requireOperatorOutranksTarget(target: User, operatorId: Long, operatorPermission: Int) {
        if (target.id == operatorId) throw ForbiddenException("不能对自己的账号执行该操作")
        if (target.permission >= operatorPermission) throw ForbiddenException("不能操作权限不低于自己的账号")
    }
    
    private companion object {
        val VALID_PERMISSIONS = setOf(User.BANNED, User.NORMAL, User.ADMIN, User.SUPER_ADMIN)
        
        /** 注册用户名规则：字母/数字/下划线，2-50 位。 */
        val USERNAME_PATTERN = Regex("^[A-Za-z0-9_]{2,50}$")
        
        /** 站点邮箱格式底线校验，完整可达性由邮箱验证流程兜底。 */
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        
        /** users.nickname 列长度上限。 */
        const val NICKNAME_MAX_LENGTH = 50
        
        /** 密码字符数下限，与 AuthService 保持一致。 */
        const val PASSWORD_MIN_LENGTH = 8
        
        /** 密码字符数上限。 */
        const val PASSWORD_MAX_LENGTH = 64
        
        /** BCrypt 只处理前 72 字节，超过会直接抛异常，这里提前拦下。 */
        const val PASSWORD_MAX_BYTES = 72
        
        /** 头像文件体积上限。 */
        const val AVATAR_MAX_BYTES = 2L * 1024 * 1024
        
        /** 头像边长范围，避免上传超大图拖垮前端渲染。 */
        const val AVATAR_MIN_SIDE = 16
        const val AVATAR_MAX_SIDE = 1024
    }
}
