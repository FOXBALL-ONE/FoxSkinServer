package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.UserCloset
import top.foxball.foxskinserver.entity.jdbc.UserClosetId

/** 用户衣柜关联数据访问仓储。 */
interface UserClosetRepository : JpaRepository<UserCloset, UserClosetId> {
    fun findUserClosetByIdUserIdAndIdTextureId(userId: Long, textureId: Long): UserCloset?
}
