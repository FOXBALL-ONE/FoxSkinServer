package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.Cape

/** 披风纹理数据访问仓储。 */
interface CapeRepository : JpaRepository<Cape, Long> {
    fun findAllByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Cape>
}
