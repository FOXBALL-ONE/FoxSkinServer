package top.foxball.foxskinserver.repository

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.UserTotpBackupCode
import java.time.LocalDateTime

interface UserTotpBackupCodeRepository : JpaRepository<UserTotpBackupCode, Long> {
    fun findAllByUserIdAndUsedAtIsNull(userId: Long): List<UserTotpBackupCode>
    fun countByUserIdAndUsedAtIsNull(userId: Long): Long
    @Modifying @Transactional
    fun deleteByUserId(userId: Long): Long
    @Modifying @Transactional
    @Query("update UserTotpBackupCode c set c.usedAt = :usedAt where c.id = :id and c.usedAt is null")
    fun consume(@Param("id") id: Long, @Param("usedAt") usedAt: LocalDateTime): Int
}
