package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

/** Microsoft/Minecraft profile UUID 与 FoxSkin 角色的绑定。 */
@Entity
@Table(
    name = "mojang_identity_bindings",
    indexes = [Index(name = "ix_mojang_binding_user_id", columnList = "user_id")],
)
class MojangIdentityBinding(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "player_id", nullable = false)
    var playerId: Long = 0,
    @Column(name = "mojang_uuid", nullable = false, length = 36)
    var mojangUuid: UUID = UUID.randomUUID(),
    @Column(name = "mojang_name", nullable = false, length = 16)
    var mojangName: String = "",
    @Column(nullable = false, length = 16)
    var status: String = ACTIVE,
    @Column(name = "verified_at", nullable = false)
    var verifiedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val ACTIVE = "ACTIVE"
        const val REVOKED = "REVOKED"
    }
}
