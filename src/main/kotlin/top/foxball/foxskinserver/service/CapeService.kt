package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Cape
import top.foxball.foxskinserver.repository.CapeRepository

@Service
class CapeService(private val repository: CapeRepository) {
    fun getCapeById(id: Long): Cape? = repository.findById(id).orElse(null)
    fun getCapesByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Cape> =
        repository.findAllByUploaderIdAndPublicTexture(uploaderId, publicTexture)
    
    fun save(cape: Cape): Cape = repository.save(cape)
    fun saveAll(capes: Iterable<Cape>): List<Cape> = repository.saveAll(capes)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
    fun deleteAll(capes: Iterable<Cape>) = repository.deleteAll(capes)
    
    /** 管理端披风列表。 */
    fun search(keyword: String, uploaderId: Long?, publicTexture: Boolean?, pageable: Pageable): Page<Cape> =
        repository.search(keyword, uploaderId, publicTexture, pageable)
    
    /** 上传者名下的全部披风。 */
    fun getCapesByUploader(uploaderId: Long): List<Cape> = repository.findAllByUploaderId(uploaderId)
    
    /** 切换披风的公开状态；目标不存在时返回 null。 */
    fun setPublic(id: Long, publicTexture: Boolean): Cape? {
        val cape = repository.findById(id).orElse(null) ?: return null
        cape.publicTexture = publicTexture
        return repository.save(cape)
    }
}
