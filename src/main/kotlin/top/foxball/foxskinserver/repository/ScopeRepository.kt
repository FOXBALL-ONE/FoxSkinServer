package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.Scope

/** OAuth 作用域数据访问仓储。 */
interface ScopeRepository : JpaRepository<Scope, Long> {
    fun findScopeByName(name: String): Scope?
}
