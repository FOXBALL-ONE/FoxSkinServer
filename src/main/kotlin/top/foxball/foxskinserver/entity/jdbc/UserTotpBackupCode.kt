package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_totp_backup_codes", indexes = [Index(name = "ix_totp_backup_user_id", columnList = "user_id")], uniqueConstraints = [UniqueConstraint(name = "uk_totp_backup_user_digest", columnNames = ["user_id", "code_digest"])])
class UserTotpBackupCode(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "code_digest", nullable = false, length = 64)
    var codeDigest: String = "",
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
