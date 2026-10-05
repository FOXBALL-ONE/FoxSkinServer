package top.foxball.foxskinserver.repository

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.UserTotp
import java.time.LocalDateTime

interface UserTotpRepository : JpaRepository<UserTotp, Long> {
    fun findByUserId(userId: Long): UserTotp?
    fun deleteByUserId(userId: Long): Long

    @Modifying
    @Transactional
    @Query(
        "update UserTotp t set t.lastUsedTimeStep = :step, t.updatedAt = :updatedAt " +
        "where t.id = :id and t.enabled = true and (t.lastUsedTimeStep is null or t.lastUsedTimeStep < :step)"
    )
    fun claimTimeStep(
        @Param("id") id: Long,
        @Param("step") step: Long,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int
}
