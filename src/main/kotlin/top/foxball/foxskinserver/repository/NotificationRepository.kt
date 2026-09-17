package top.foxball.foxskinserver.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.Notification

/** 站内通知数据访问仓储。 */
interface NotificationRepository : JpaRepository<Notification, String> {
    fun findAllByNotifiableTypeAndNotifiableId(notifiableType: String, notifiableId: Long): List<Notification>
    
    /** 管理端通知检索；[notifiableType] 传空串、[notifiableId] 传 null 表示不过滤。 */
    @Query(
        """
        select n from Notification n
        where (:notifiableType = '' or n.notifiableType = :notifiableType)
          and (:notifiableId is null or n.notifiableId = :notifiableId)
        """
    )
    fun search(
        @Param("notifiableType") notifiableType: String,
        @Param("notifiableId") notifiableId: Long?,
        pageable: Pageable,
    ): Page<Notification>
}
