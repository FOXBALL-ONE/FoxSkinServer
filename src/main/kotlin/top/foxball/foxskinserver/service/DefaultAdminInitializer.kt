package top.foxball.foxskinserver.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import top.foxball.foxskinserver.config.DefaultAdminProperties
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.repository.UserRepository

@Component
class DefaultAdminInitializer(
    private val properties: DefaultAdminProperties,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        
        val email = properties.email.trim().lowercase()
        val username = properties.username.trim()
        require(email.isNotBlank()) { "启用默认管理员时必须设置 DEFAULT_ADMIN_EMAIL" }
        require(username.isNotBlank()) { "启用默认管理员时必须设置 DEFAULT_ADMIN_USERNAME" }
        val password = requireNotNull(properties.password) { "启用默认管理员时必须设置 DEFAULT_ADMIN_PASSWORD" }
        require(password.isNotBlank()) { "启用默认管理员时必须设置 DEFAULT_ADMIN_PASSWORD" }
        val fixedToken = properties.fixedToken
        if (fixedToken.enabled) {
            require(fixedToken.token.isNotBlank()) {
                "启用默认管理员固定令牌时必须设置 DEFAULT_ADMIN_FIXED_TOKEN"
            }
        }
        
        val user = userRepository.findByEmail(email) ?: userRepository.save(
            User(
                email = email,
                username = username,
                password = requireNotNull(passwordEncoder.encode(password)),
                nickname = username,
                permission = User.ADMIN,
                verified = true,
            )
        )
        logger.info("默认管理员账户已就绪: {}", email)
        
        if (!fixedToken.enabled) return
        logger.info("默认管理员固定令牌已启用: {}", email)
    }
    
    private companion object {
        val logger = LoggerFactory.getLogger(DefaultAdminInitializer::class.java)
    }
}
