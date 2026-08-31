package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Notification
import top.foxball.foxskinserver.repository.NotificationRepository

@Service
class NotificationService(private val repository: NotificationRepository) {
    fun getNotificationById(id: String): Notification? = repository.findById(id).orElse(null)
    fun getNotificationsByNotifiableTypeAndNotifiableId(type: String, id: Long): List<Notification> = repository.findAllByNotifiableTypeAndNotifiableId(type, id)
    fun save(notification: Notification): Notification = repository.save(notification)
    fun saveAll(notifications: Iterable<Notification>): List<Notification> = repository.saveAll(notifications)
    fun deleteById(id: String) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<String>) = repository.deleteAllById(ids)
}
