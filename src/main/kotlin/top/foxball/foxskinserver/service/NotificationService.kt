package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Notification
import top.foxball.foxskinserver.repository.NotificationRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
class NotificationService(private val repository: NotificationRepository) {
    fun getNotificationById(id: String): Notification? = repository.findById(id).orElse(null)
    fun getNotificationsByNotifiableTypeAndNotifiableId(type: String, id: Long): List<Notification> =
        repository.findAllByNotifiableTypeAndNotifiableId(type, id)
    
    fun save(notification: Notification): Notification = repository.save(notification)
    fun saveAll(notifications: Iterable<Notification>): List<Notification> = repository.saveAll(notifications)
    fun deleteById(id: String) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<String>) = repository.deleteAllById(ids)
    
    /** 管理端通知列表。 */
    fun search(notifiableType: String, notifiableId: Long?, pageable: Pageable): Page<Notification> =
        repository.search(notifiableType, notifiableId, pageable)
    
    /** 管理员下发一条站内通知，主键与时间戳在此生成。 */
    fun create(notifiableType: String, notifiableId: Long, type: String, data: String): Notification {
        val now = LocalDateTime.now()
        return repository.save(
            Notification(
                id = UUID.randomUUID().toString(),
                type = type,
                notifiableType = notifiableType,
                notifiableId = notifiableId,
                data = data,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
