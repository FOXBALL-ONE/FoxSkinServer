package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.UserCloset
import top.foxball.foxskinserver.entity.jdbc.UserClosetId
import top.foxball.foxskinserver.repository.UserClosetRepository

@Service
class UserClosetService(private val repository: UserClosetRepository) {
    fun getUserClosetById(id: UserClosetId): UserCloset? = repository.findById(id).orElse(null)
    fun getUserClosetByUserIdAndTextureId(userId: Long, textureId: Long): UserCloset? = repository.findUserClosetByIdUserIdAndIdTextureId(userId, textureId)
    fun save(closet: UserCloset): UserCloset = repository.save(closet)
    fun saveAll(closets: Iterable<UserCloset>): List<UserCloset> = repository.saveAll(closets)
    fun deleteById(id: UserClosetId) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<UserClosetId>) = repository.deleteAllById(ids)
}
