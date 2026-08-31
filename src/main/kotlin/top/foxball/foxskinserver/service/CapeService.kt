package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Cape
import top.foxball.foxskinserver.repository.CapeRepository

@Service
class CapeService(private val repository: CapeRepository) {
    fun getCapeById(id: Long): Cape? = repository.findById(id).orElse(null)
    fun getCapesByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Cape> = repository.findAllByUploaderIdAndPublicTexture(uploaderId, publicTexture)
    fun save(cape: Cape): Cape = repository.save(cape)
    fun saveAll(capes: Iterable<Cape>): List<Cape> = repository.saveAll(capes)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
