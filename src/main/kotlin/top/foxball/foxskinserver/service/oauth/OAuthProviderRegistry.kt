package top.foxball.foxskinserver.service.oauth

import org.springframework.stereotype.Component

/** 汇集所有已注册提供商；是否启用必须实时读取，支持运行时开关。 */
@Component
class OAuthProviderRegistry(private val providers: List<OAuthProvider>) {
    fun all(): List<OAuthProvider> = providers.filter { it.enabled }

    fun byId(id: String): OAuthProvider? = providers.firstOrNull { it.id == id && it.enabled }
}