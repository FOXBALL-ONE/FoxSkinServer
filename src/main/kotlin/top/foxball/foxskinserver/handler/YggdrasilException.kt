package top.foxball.foxskinserver.handler

import org.springframework.http.HttpStatus

/** Yggdrasil 客户端约定的错误结构，不能使用站内统一 Response 包装。 */
class YggdrasilException(
    val httpStatus: HttpStatus,
    val error: String,
    override val message: String,
    val causeMessage: String = "",
    val retryAfterSeconds: Long = 0,
) : RuntimeException(message)
