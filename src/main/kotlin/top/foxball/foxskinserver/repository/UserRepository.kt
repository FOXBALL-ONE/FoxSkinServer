package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.User

interface UserRepository : JpaRepository<User, Long> {
    fun findUserById(id: Long): User? = findById(id).orElse(null)
    fun findByEmail(email: String): User?
}
