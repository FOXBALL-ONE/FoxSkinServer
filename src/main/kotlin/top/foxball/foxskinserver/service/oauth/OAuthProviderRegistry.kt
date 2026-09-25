package top.foxball.foxskinserver.service.oauth

import org.springframework.stereotype.Component

/** 汇集所有凭据配置齐备的提供商，供路由与前端列表查询。 */
@Component
class OAuthProviderRegistry(providers: List<OAuthProvider>) {
    private val enabledProviders = providers.filter { it.enabled }

    /** 当前启用的提供商，按 Spring Bean 声明顺序。 */
    fun all(): List<OAuthProvider> = enabledProviders

    fun byId(id: String): OAuthProvider? = enabledProviders.firstOrNull { it.id == id }
}
