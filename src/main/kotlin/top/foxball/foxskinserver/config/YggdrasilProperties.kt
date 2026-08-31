package top.foxball.foxskinserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Yggdrasil 外置登录协议配置。敏感的 RSA 私钥只应通过环境变量或密钥管理服务注入。 */
@ConfigurationProperties(prefix = "shopmall.yggdrasil")
data class YggdrasilProperties(
    /** 对外暴露的协议根路径，authlib-injector 会以此路径拼接所有接口。 */
    var apiPath: String = "/api/yggdrasil",
    var serverName: String = "FoxSkinServer",
    var implementationName: String = "FoxSkinServer",
    var implementationVersion: String = "0.0.1",
    /** 是否要求用户完成邮箱验证后才能使用外置登录。 */
    var requireVerified: Boolean = false,
    /** 允许客户端加载材质的域名白名单。 */
    var skinDomains: List<String> = listOf("localhost"),
    /** 材质 URL 的基础地址，留空时复用 file.base-url。 */
    var textureBaseUrl: String = "",
    /** PKCS#8 或 PKCS#1 PEM 格式 RSA 私钥；未配置时仅为开发环境生成临时密钥。 */
    var privateKeyPem: String = "",
    /** accessToken 可直接用于认证和 join 的有效期，默认 3 天。 */
    var tokenExpireSeconds: Long = 259200,
    /** accessToken 允许 refresh 的最长时间，默认 7 天。 */
    var refreshExpireSeconds: Long = 604800,
    /** join 凭证的短暂缓存时间，避免长期占用 Redis。 */
    var joinExpireSeconds: Long = 120,
    /** 批量名称查询最多接受的角色数量，遵循 Blessing Skin 默认值 5。 */
    var profileSearchMax: Int = 5,
    /** authenticate/signout 同一账号两次请求的最小间隔，0 表示关闭节流。 */
    var throttleIntervalMillis: Long = 1000,
)
