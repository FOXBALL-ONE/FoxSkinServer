package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.MojangIdentityBinding
import java.util.UUID

interface MojangIdentityBindingRepository : JpaRepository<MojangIdentityBinding, Long> {
    fun findAllByUserIdAndStatusOrderByCreatedAtDesc(userId: Long, status: String): List<MojangIdentityBinding>
    fun findByIdAndUserId(id: Long, userId: Long): MojangIdentityBinding?
    fun findAllByMojangUuidOrderByUpdatedAtDesc(mojangUuid: UUID): List<MojangIdentityBinding>
    fun findAllByPlayerIdOrderByUpdatedAtDesc(playerId: Long): List<MojangIdentityBinding>
    fun findAllByMojangUuidAndStatus(mojangUuid: UUID, status: String): List<MojangIdentityBinding>
    fun findByPlayerIdAndStatus(playerId: Long, status: String): MojangIdentityBinding?
}
