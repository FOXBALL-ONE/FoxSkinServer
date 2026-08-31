package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.Notification

/** 站内通知数据访问仓储。 */
interface NotificationRepository : JpaRepository<Notification, String> {
    fun findAllByNotifiableTypeAndNotifiableId(notifiableType: String, notifiableId: Long): List<Notification>
}
