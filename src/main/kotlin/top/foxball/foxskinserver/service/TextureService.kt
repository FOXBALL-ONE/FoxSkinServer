package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Texture
import top.foxball.foxskinserver.repository.TextureRepository

@Service
class TextureService(private val repository: TextureRepository) {
    fun getTextureById(id: Long): Texture? = repository.findByIdWithFile(id)
    fun getTextureByHash(hash: String): Texture? = repository.findByHash(hash)
    fun getTexturesByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Texture> = repository.findAllByUploaderIdAndPublicTexture(uploaderId, publicTexture)
    fun save(texture: Texture): Texture = repository.save(texture)
    fun saveAll(textures: Iterable<Texture>): List<Texture> = repository.saveAll(textures)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
