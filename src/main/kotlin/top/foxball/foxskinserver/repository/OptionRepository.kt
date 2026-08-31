package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.Option

/** 站点配置数据访问仓储。 */
interface OptionRepository : JpaRepository<Option, Long> {
    fun findOptionByName(name: String): Option?
}
