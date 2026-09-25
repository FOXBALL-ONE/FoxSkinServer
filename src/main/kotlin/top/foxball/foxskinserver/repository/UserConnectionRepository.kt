package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.UserConnection

interface UserConnectionRepository : JpaRepository<UserConnection, Long> {
    fun findByProviderAndOpenId(provider: String, openId: String): UserConnection?
    fun findAllByUserId(userId: Long): List<UserConnection>
    fun findByUserIdAndProvider(userId: Long, provider: String): UserConnection?
    fun deleteByUserIdAndProvider(userId: Long, provider: String): Long
}
