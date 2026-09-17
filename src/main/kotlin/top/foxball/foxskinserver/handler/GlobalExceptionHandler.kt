package top.foxball.foxskinserver.handler

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder
import top.foxball.foxskinserver.config.YggdrasilProperties

/** 全局异常处理：将各类异常转换为统一 [Response] 响应。 */
@Order(2)
@RestControllerAdvice
class GlobalExceptionHandler(
    private val yggdrasilProperties: YggdrasilProperties = YggdrasilProperties(),
) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val builder = ResponseBuilder()
    
    /** Yggdrasil 客户端要求错误响应使用 error/errorMessage，而不是站内 Response 包装。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun onHttpMediaTypeNotSupported(
        req: HttpServletRequest,
        ex: HttpMediaTypeNotSupportedException,
    ): ResponseEntity<*> {
        if (isYggdrasilRequest(req)) return yggdrasilError(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported Media Type",
            "请求必须使用 application/json",
        )
        return builder.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .message(ex.message ?: "不支持的媒体类型")
            .build()
    }
    
    @ExceptionHandler(HomeRecommendationVersionConflictException::class)
    fun onHomeRecommendationVersionConflictException(
        ex: HomeRecommendationVersionConflictException,
    ): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("actual_version")
            val actualVersion: Long,
        )
        
        val rs = Response(actualVersion = ex.actualVersion)
        return builder.status(ex.status)
            .message(ex.message)
            .data(rs)
            .build()
    }
    
    @ExceptionHandler(AnnouncementVersionConflictException::class)
    fun onAnnouncementVersionConflictException(
        ex: AnnouncementVersionConflictException,
    ): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("actual_version")
            val actualVersion: Long,
        )
        
        val rs = Response(actualVersion = ex.actualVersion)
        return builder.status(ex.status)
            .message(ex.message)
            .data(rs)
            .build()
    }
    
    @ExceptionHandler(AccessTokenExpiredException::class)
    fun onAccessTokenExpiredException(ex: AccessTokenExpiredException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .message(ex.message)
            .data(mapOf("error" to AccessTokenExpiredException.ERROR))
            .build()
    }
    
    @ExceptionHandler(RefreshTokenExpiredException::class)
    fun onRefreshTokenExpiredException(ex: RefreshTokenExpiredException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .message(ex.message)
            .data(mapOf("error" to RefreshTokenExpiredException.ERROR))
            .build()
    }
    
    @ExceptionHandler(BusinessException::class)
    fun onBusinessException(ex: BusinessException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .message(ex.message)
            .build()
    }
    
    /** Yggdrasil 客户端不识别站内 Response 包装，必须返回协议规定的错误字段。 */
    @ExceptionHandler(YggdrasilException::class)
    fun onYggdrasilException(ex: YggdrasilException): ResponseEntity<Map<String, String>> {
        val body = linkedMapOf("error" to ex.error, "errorMessage" to ex.message)
        if (ex.causeMessage.isNotBlank()) body["cause"] = ex.causeMessage
        val response = ResponseEntity.status(ex.httpStatus)
        if (ex.retryAfterSeconds > 0) response.header("Retry-After", ex.retryAfterSeconds.toString())
        return response.body(body)
    }
    
    @ExceptionHandler(OrderProcessingException::class)
    fun onOrderProcessingException(ex: OrderProcessingException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(1)
            .message(ex.message)
            .build()
    }
    
    @ExceptionHandler(OrderWindowLimitException::class)
    fun onOrderWindowLimitException(ex: OrderWindowLimitException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(ex.retryAfterSeconds)
            .message(ex.message)
            .build()
    }
    
    @ExceptionHandler(SupportTicketRateLimitException::class)
    fun onSupportTicketRateLimitException(ex: SupportTicketRateLimitException): ResponseEntity<Response> {
        return builder.status(ex.status)
            .retryAfter(ex.retryAfterSeconds)
            .message(ex.message)
            .build()
    }
    
    
    @ExceptionHandler(AccessDeniedException::class)
    fun onAccessDeniedException(ex: AccessDeniedException): ResponseEntity<Response> {
        return builder.forbidden()
            .message(ex.message ?: "禁止访问")
            .build()
    }
    
    /** 业务代码或方法级安全校验抛出的认证异常统一转换为 401。 */
    @ExceptionHandler(AuthenticationException::class)
    fun onAuthenticationException(ex: AuthenticationException): ResponseEntity<Response> {
        return builder.unauthorized()
            .message(ex.message ?: "未授权")
            .build()
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onHttpRequestMethodNotSupportedException(
        req: HttpServletRequest,
        ex: HttpRequestMethodNotSupportedException,
    ): ResponseEntity<*> {
        if (isYggdrasilRequest(req)) return yggdrasilError(
            HttpStatus.METHOD_NOT_ALLOWED,
            "MethodNotAllowedException",
            "不支持的请求方法。",
        )
        return builder.badRequest()
            .message("Method \"${ex.method}\" is not supported on this endpoint.")
            .build()
    }
    
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun onNoResourceOrHandlerFoundException(req: HttpServletRequest): ResponseEntity<*> {
        if (isYggdrasilRequest(req)) return yggdrasilError(
            HttpStatus.NOT_FOUND,
            "NotFoundOperationException",
            "请求的接口不存在。",
        )
        return builder.notFound().build()
    }
    
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun onMissingServletRequestParameterException(ex: MissingServletRequestParameterException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required parameter \"${ex.parameterName}\" is not provided!")
            .build()
    }
    
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun onMissingRequestHeaderException(ex: MissingRequestHeaderException): ResponseEntity<Response> {
        return builder.badRequest()
            .message("Required request header \"${ex.headerName}\" is not provided!")
            .build()
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun onMethodArgumentTypeMismatchException(
        req: HttpServletRequest,
        ex: MethodArgumentTypeMismatchException,
    ): ResponseEntity<*> {
        if (isYggdrasilRequest(req)) return yggdrasilError(
            HttpStatus.BAD_REQUEST,
            "IllegalArgumentException",
            "请求参数格式错误。",
        )
        return builder.badRequest()
            .message("Parameter \"${ex.parameter.parameterName}\" type mismatch. Expected ${ex.requiredType}.")
            .build()
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<Response> {
        val detail = ex.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return builder.badRequest()
            .message("参数校验失败：$detail")
            .build()
    }
    
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun onHandlerMethodValidationException(ex: HandlerMethodValidationException): ResponseEntity<Response> {
        val detail = ex.parameterValidationResults.joinToString("; ") { result ->
            val parameterName = result.methodParameter.parameterName ?: "parameter"
            val messages = result.resolvableErrors.joinToString(", ") { error ->
                error.defaultMessage ?: "invalid value"
            }
            "$parameterName: $messages"
        }
        return builder.badRequest()
            .message(if (detail.isBlank()) "参数校验失败" else "参数校验失败: $detail")
            .build()
    }
    
    /**
     * 类上标注 @Validated 的控制器由 AOP 方法校验拦截，校验失败抛的是 ConstraintViolationException，
     * 与 Spring MVC 内建校验的 HandlerMethodValidationException 不是同一种异常，需要单独映射为 400。
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun onConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<Response> {
        val detail = ex.constraintViolations.joinToString("; ") { violation ->
            val parameterName = violation.propertyPath.toString().substringAfterLast('.')
            val message = violation.message ?: "invalid value"
            "$parameterName: $message"
        }
        return builder.badRequest()
            .message(if (detail.isBlank()) "参数校验失败" else "参数校验失败: $detail")
            .build()
    }
    
    
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onHttpMessageNotReadable(req: HttpServletRequest, ex: HttpMessageNotReadableException): ResponseEntity<*> {
        if (isYggdrasilRequest(req)) return yggdrasilError(
            HttpStatus.BAD_REQUEST,
            "IllegalArgumentException",
            "请求体必须是合法 JSON",
        )
        return builder.badRequest()
            .message("请求体格式错误或必填字段缺失")
            .build()
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun onMaxUploadSizeExceededException(): ResponseEntity<Response> {
        return builder.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .message("Uploaded file exceeds the configured size limit.")
            .build()
    }
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun onIllegalArgumentException(ex: IllegalArgumentException?): ResponseEntity<Response> {
        log.warn("Illegal argument access happened: ", ex)
        return builder.badRequest()
            .message(ex?.message ?: "Invalid argument.")
            .build()
    }
    
    @ExceptionHandler(TransientDataAccessException::class)
    fun onTransientDataAccessException(ex: TransientDataAccessException): ResponseEntity<Response> {
        log.warn("Transient data access error: {}", ex.message)
        return builder.serviceUnavailable()
            .retryAfter(1)
            .message("系统繁忙，请稍后重试")
            .build()
    }
    
    
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun onOptimisticLockingFailureException(ex: ObjectOptimisticLockingFailureException): ResponseEntity<Response> {
        log.warn("Optimistic locking conflict: {}", ex.message)
        return builder.status(HttpStatus.CONFLICT)
            .message("数据已被其他操作更新，请刷新后重试")
            .build()
    }
    
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun onDataIntegrityViolationException(ex: DataIntegrityViolationException): ResponseEntity<Response> {
        val detail = generateSequence<Throwable>(ex) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        val message = when {
            "uk_shipment_item_active" in detail -> "商品已分配给其他有效运单"
            "uk_shipment_carrier_tracking" in detail -> "承运商追踪号已绑定其他运单"
            "uk_logistics_idempotency" in detail -> "幂等键冲突，请重试查询原结果"
            "uk_order_idempotency" in detail -> "下单幂等键冲突，请重试查询原订单"
            "fk_support_ticket_message_attachment_file" in detail -> "工单消息使用中的附件不能删除"
            else -> null
        }
        if (message != null) {
            return builder.status(HttpStatus.CONFLICT).message(message).build()
        }
        log.error("Unhandled data integrity violation", ex)
        return builder.exception().build()
    }
    
    @ExceptionHandler(DataAccessException::class)
    fun onDataAccessException(ex: DataAccessException): ResponseEntity<Response> {
        log.error("Non-transient data access error", ex)
        return builder.exception().build()
    }
    
    @ExceptionHandler(Exception::class)
    fun onException(req: HttpServletRequest, ex: Exception?): ResponseEntity<Response> {
        log.error("Got an exception while process request: {}", req.requestURI, ex)
        return builder.exception().build()
    }
    
    /** Yggdrasil 路由不能混用站内统一响应，且需要兼容反向代理的 context-path。 */
    private fun isYggdrasilRequest(req: HttpServletRequest): Boolean {
        val apiPath = yggdrasilProperties.apiPath.trimEnd('/').ifEmpty { "/" }
        val requestPath = req.requestURI.removePrefix(req.contextPath).trimEnd('/').ifEmpty { "/" }
        return requestPath == apiPath || requestPath.startsWith("$apiPath/")
    }
    
    private fun yggdrasilError(
        status: HttpStatus,
        error: String,
        message: String,
    ): ResponseEntity<Map<String, String>> = ResponseEntity.status(status)
        .body(linkedMapOf("error" to error, "errorMessage" to message))
}
