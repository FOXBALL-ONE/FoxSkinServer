package top.foxball.foxskinserver.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.User

interface UserRepository : JpaRepository<User, Long> {
    fun findUserById(id: Long): User? = findById(id).orElse(null)
    fun findByEmail(email: String): User?
    fun findByUsername(username: String): User?
    
    /** 管理端用户检索；[keyword] 传空串表示不按关键字过滤，[permission] 传 null 表示不按权限等级过滤。 */
    @Query(
        """
        select u from User u
        where (:keyword = ''
            or lower(u.username) like lower(concat('%', :keyword, '%'))
            or lower(u.email) like lower(concat('%', :keyword, '%'))
            or lower(u.nickname) like lower(concat('%', :keyword, '%')))
          and (:permission is null or u.permission = :permission)
        """
    )
    fun search(
        @Param("keyword") keyword: String,
        @Param("permission") permission: Int?,
        pageable: Pageable,
    ): Page<User>
}
