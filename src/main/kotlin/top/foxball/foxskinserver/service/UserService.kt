package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.User
import top.foxball.foxskinserver.repository.UserRepository

@Service
class UserService(
    private  var userRepository: UserRepository
) {
    
    fun getUserById(id: Long): User?{
        return userRepository.findUserById(id)
    }

    fun findById(id: Long): User? = getUserById(id)
    
    fun save(user: User): User {
        return userRepository.save(user)
    }

    fun saveAll(users: Iterable<User>): List<User> = userRepository.saveAll(users)

    fun deleteById(id: Long) = userRepository.deleteById(id)

    fun deleteAllById(ids: Iterable<Long>) = userRepository.deleteAllById(ids)
}
