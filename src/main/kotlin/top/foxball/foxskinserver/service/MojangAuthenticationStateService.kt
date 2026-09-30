package top.foxball.foxskinserver.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.foxskinserver.config.MojangProperties
import top.foxball.foxskinserver.entity.jdbc.Option
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.repository.OptionRepository
import top.foxball.foxskinserver.security.YggdrasilTokenStore
import java.time.LocalDateTime

data class MojangAuthenticationState(
    val enabled: Boolean,
    val source: String,
    val updatedBy: String,
    val updatedAt: LocalDateTime,
)

/** 正版混合认证运行时开关。数据库 options 是最终来源，多实例每次读取同一持久化状态。 */
@Service
class MojangAuthenticationStateService(
    private val optionRepository: OptionRepository,
    private val redis: StringRedisTemplate,
    private val tokenStore: YggdrasilTokenStore,
    private val properties: MojangProperties,
) {
    fun get(): MojangAuthenticationState {
        if (!properties.runtimeToggle) return startupState()
        val option = optionRepository.findOptionByName(OPTION_NAME) ?: return startupState()
        val fields = option.value.split('|', limit = 3)
        return MojangAuthenticationState(
            enabled = fields.firstOrNull()?.equals("true", ignoreCase = true) == true,
            source = "DATABASE",
            updatedBy = fields.getOrNull(1) ?: "system",
            updatedAt = fields.getOrNull(2)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now(),
        )
    }

    fun requireEnabled(): MojangAuthenticationState = get().also {
        if (!it.enabled) throw ParamErrorException("正版混合认证未启用，请先在管理端开启")
    }

    @Transactional
    fun update(enabled: Boolean, operator: String): MojangAuthenticationState {
        if (!properties.runtimeToggle) throw ParamErrorException("正版混合认证开关未启用运行时更新")
        val now = LocalDateTime.now()
        val option = optionRepository.findOptionByName(OPTION_NAME) ?: Option(name = OPTION_NAME)
        option.value = listOf(enabled.toString(), operator, now.toString()).joinToString("|")
        optionRepository.save(option)
        redis.delete(VERSION_KEY)
        val revokedJoinCredentials = if (enabled) 0 else tokenStore.clearExternalJoins()
        val state = MojangAuthenticationState(enabled, "DATABASE", operator, now)
        log.info(
            "Mojang 混合认证开关更新: enabled={}, operator={}, revoked_join_credentials={}",
            enabled, operator, revokedJoinCredentials,
        )
        return state
    }

    private fun startupState() = MojangAuthenticationState(
        enabled = properties.enabled,
        source = "STARTUP",
        updatedBy = "system",
        updatedAt = LocalDateTime.now(),
    )

    companion object {
        private val log = LoggerFactory.getLogger(MojangAuthenticationStateService::class.java)
        private const val OPTION_NAME = "mojang_authentication_enabled"
        private const val VERSION_KEY = "mojang:authentication:version"
    }
}