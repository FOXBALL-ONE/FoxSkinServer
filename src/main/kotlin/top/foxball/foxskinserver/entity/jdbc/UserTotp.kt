package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_totp",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_totp_user_id", columnNames = ["user_id"])]
)
class UserTotp(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "secret_ciphertext", nullable = false, length = 1024)
    var secretCiphertext: String = "",
    @Column(name = "pending_secret_ciphertext", length = 1024)
    var pendingSecretCiphertext: String? = null,
    @Column(nullable = false)
    var enabled: Boolean = false,
    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null,
    @Column(name = "last_used_time_step")
    var lastUsedTimeStep: Long? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
