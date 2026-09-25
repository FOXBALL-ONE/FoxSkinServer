package top.foxball.foxskinserver.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.config.JwtProperties
import top.foxball.foxskinserver.config.OAuthProperties
import top.foxball.foxskinserver.handler.BusinessException
import top.foxball.foxskinserver.handler.ResourceNotFoundException
import top.foxball.foxskinserver.handler.UnauthorizedException
import top.foxball.foxskinserver.security.AuthenticatedUser
import top.foxball.foxskinserver.security.OAuthStateStore
import top.foxball.foxskinserver.service.AuthService
import top.foxball.foxskinserver.service.oauth.OAuthConnectionService
import top.foxball.foxskinserver.service.oauth.OAuthProviderRegistry
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * 第三方登录（OAuth/OIDC 扩展组件）对外端点。
 *
 * redirect 返回授权页地址由前端整页跳转；callback 是提供商回跳的落点，处理完后
 * 以 302 带回前端 `/oauth/callback` 页面，令牌放 URL fragment 避免进服务端日志与 Referer。
 */
@RestController
class OAuthController(
    private val registry: OAuthProviderRegistry,
    private val stateStore: OAuthStateStore,
    private val connectionService: OAuthConnectionService,
    private val authService: AuthService,
    private val responseBuilder: ResponseBuilder,
    private val oauthProperties: OAuthProperties,
    private val jwtProperties: JwtProperties,
) {
    @GetMapping("/api/auth/oauth/providers")
    fun providers(): ResponseEntity<Response> {
        data class ProviderData(
            val id: String,
            @param:JsonProperty("display_name") val displayName: String,
        )
        
        val rs = registry.all().map { ProviderData(it.id, it.displayName) }
        return responseBuilder.ok().data(rs).build()
    }
    
    @GetMapping("/api/auth/oauth/{provider}/redirect")
    fun redirect(
        @PathVariable("provider") provider: String,
        @RequestParam("mode", required = false) mode: String?,
        @RequestParam("redirect", required = false) redirect: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
        request: HttpServletRequest,
    ): ResponseEntity<Response> {
        data class AuthorizeData(
            @param:JsonProperty("authorize_url") val authorizeUrl: String,
        )
        
        val impl = registry.byId(provider)
            ?: throw ResourceNotFoundException("未启用该登录方式")
        val isBind = mode == "bind"
        val userId = if (isBind) principal?.userId ?: throw UnauthorizedException("绑定第三方账号前请先登录") else 0
        val safeRedirect = if (isBind) "" else sanitizePath(redirect)
        val state = stateStore.issue(
            OAuthStateStore.StatePayload(
                provider = impl.id,
                mode = if (isBind) OAuthStateStore.Mode.BIND else OAuthStateStore.Mode.LOGIN,
                redirect = safeRedirect,
                userId = userId,
            ),
        )
        val authorizeUrl = impl.authorizeUrl(callbackUrl(impl.id, request), state)
        return responseBuilder.ok().data(AuthorizeData(authorizeUrl)).build()
    }
    
    @GetMapping("/api/auth/oauth/{provider}/callback")
    fun callback(
        @PathVariable("provider") provider: String,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("state", required = false) state: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        val payload = state?.let { stateStore.consume(it, provider) }
        val impl = registry.byId(provider)
        if (payload == null || impl == null) {
            return redirectToFrontend(request, "error=${encode("授权状态已过期，请重新发起登录")}")
        }
        if (code.isNullOrBlank()) {
            return redirectToFrontend(request, "error=${encode("提供商未返回授权码，请重新发起登录")}")
        }
        return try {
            val identity = impl.exchange(code, callbackUrl(impl.id, request))
            when (payload.mode) {
                OAuthStateStore.Mode.BIND -> {
                    connectionService.bind(payload.userId, impl.id, identity)
                    redirectToFrontend(request, "bind=ok&provider=${encode(impl.id)}")
                }
                
                OAuthStateStore.Mode.LOGIN -> {
                    val user = connectionService.findOrCreateUser(impl.id, identity)
                    val tokens = authService.issue(AuthenticatedUser.from(user))
                    response.addHeader("Set-Cookie", refreshCookie(tokens.refreshToken).toString())
                    redirectToFrontend(
                        request,
                        "token=${encode(tokens.accessToken)}&expires_in=${tokens.expiresIn}&redirect=${encode(payload.redirect)}",
                    )
                }
            }
        } catch (exception: BusinessException) {
            redirectToFrontend(request, "error=${encode(exception.message ?: "登录失败，请稍后再试")}")
        }
    }
    
    @GetMapping("/api/auth/connections")
    fun connections(@AuthenticationPrincipal principal: AuthenticatedUser): ResponseEntity<Response> {
        data class ConnectionData(
            val provider: String,
            @param:JsonProperty("open_id") val openId: String,
            val nickname: String,
            @param:JsonProperty("avatar_url") val avatarUrl: String?,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
        )
        
        val rs = connectionService.listConnections(principal.userId).map { connection ->
            ConnectionData(
                connection.provider,
                connection.openId,
                connection.nickname,
                connection.avatarUrl,
                connection.createdAt,
            )
        }
        return responseBuilder.ok().data(rs).build()
    }
    
    @DeleteMapping("/api/auth/connections/{provider}")
    fun unbind(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable("provider") provider: String,
    ): ResponseEntity<Response> {
        connectionService.unbind(principal.userId, provider)
        return responseBuilder.ok().build()
    }
    
    /** redirect 参数只接受站内相对路径，防开放重定向。 */
    private fun sanitizePath(value: String?): String =
        if (value != null && value.startsWith("/") && !value.startsWith("//")) value else "/dashboard"
    
    /**
     * 授权回调地址的站点基址：优先显式配置，同域反代部署时退回代理注入的转发头或请求本身。
     * 发起授权与回调两处必须得到同一个基址，否则提供商校验 redirect_uri 会失败。
     */
    private fun baseUrl(request: HttpServletRequest): String {
        oauthProperties.publicUrl.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
        val proto = request.getHeader("X-Forwarded-Proto") ?: request.scheme
        val host = request.getHeader("X-Forwarded-Host") ?: hostWithPort(request)
        return "$proto://$host"
    }
    
    private fun hostWithPort(request: HttpServletRequest): String {
        val port = request.serverPort
        return if (port == 80 || port == 443) request.serverName else "${request.serverName}:$port"
    }
    
    private fun callbackUrl(providerId: String, request: HttpServletRequest): String =
        "${baseUrl(request)}/api/auth/oauth/$providerId/callback"
    
    private fun redirectToFrontend(request: HttpServletRequest, fragment: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "${frontendBase(request)}/oauth/callback#$fragment")
            .build()
    
    private fun frontendBase(request: HttpServletRequest): String {
        oauthProperties.frontendUrl.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
        return baseUrl(request)
    }
    
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    
    private fun refreshCookie(token: String): ResponseCookie {
        val config = jwtProperties.refresh.cookie
        val builder = ResponseCookie.from(config.name, token)
            .httpOnly(config.httpOnly)
            .secure(config.secure)
            .path(config.path)
            .sameSite(config.sameSite)
            .maxAge(jwtProperties.refresh.ttlSeconds)
        if (config.domain.isNotBlank()) builder.domain(config.domain)
        return builder.build()
    }
}
