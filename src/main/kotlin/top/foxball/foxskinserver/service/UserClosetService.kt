package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.UserCloset
import top.foxball.foxskinserver.entity.jdbc.UserClosetId
import top.foxball.foxskinserver.handler.ResourceNotFoundException
import top.foxball.foxskinserver.repository.ClosetEntryView
import top.foxball.foxskinserver.repository.UserClosetRepository

@Service
class UserClosetService(
    private val repository: UserClosetRepository,
    private val textureService: TextureService,
) {
    fun getUserClosetById(id: UserClosetId): UserCloset? = repository.findById(id).orElse(null)
    fun getUserClosetByUserIdAndTextureId(userId: Long, textureId: Long): UserCloset? =
        repository.findUserClosetByIdUserIdAndIdTextureId(userId, textureId)
    
    fun save(closet: UserCloset): UserCloset = repository.save(closet)
    fun saveAll(closets: Iterable<UserCloset>): List<UserCloset> = repository.saveAll(closets)
    fun deleteById(id: UserClosetId) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<UserClosetId>) = repository.deleteAllById(ids)
    fun deleteAll(closets: Iterable<UserCloset>) = repository.deleteAll(closets)
    
    /** 用户衣柜内的全部关联记录。 */
    fun getUserClosetsByUserId(userId: Long): List<UserCloset> = repository.findAllByIdUserId(userId)
    
    /** 衣柜列表，连同所指向材质的展示信息一起返回。 */
    fun searchCloset(userId: Long, kind: String, keyword: String, pageable: Pageable): Page<ClosetEntryView> =
        repository.searchCloset(userId, kind, keyword, pageable)
    
    /** 把材质收进衣柜；材质不存在抛 404，重复收藏直接返回已有条目而不是报错。 */
    fun addToCloset(userId: Long, textureId: Long, itemName: String?): UserCloset {
        textureService.getTextureById(textureId) ?: throw ResourceNotFoundException("材质不存在")
        repository.findUserClosetByIdUserIdAndIdTextureId(userId, textureId)?.let { return it }
        return repository.save(
            UserCloset(
                id = UserClosetId(userId = userId, textureId = textureId),
                itemName = itemName?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
    }
    
    /** 重命名衣柜条目中的自定义名称；条目不存在返回 false。传空串表示清掉自定义名。 */
    fun renameClosetEntry(userId: Long, textureId: Long, itemName: String?): Boolean {
        val entry = repository.findUserClosetByIdUserIdAndIdTextureId(userId, textureId) ?: return false
        entry.itemName = itemName?.trim()?.takeIf { it.isNotEmpty() }
        repository.save(entry)
        return true
    }
    
    /** 把材质移出衣柜；条目不存在返回 false。 */
    fun removeFromCloset(userId: Long, textureId: Long): Boolean {
        val entry = repository.findUserClosetByIdUserIdAndIdTextureId(userId, textureId) ?: return false
        repository.delete(entry)
        return true
    }
}
