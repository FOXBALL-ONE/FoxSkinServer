package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import top.foxball.foxskinserver.entity.jdbc.Cape
import top.foxball.foxskinserver.entity.jdbc.Texture
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.repository.TextureRepository
import java.time.LocalDateTime

@Service
class TextureService(
    private val repository: TextureRepository,
    private val fileService: FileService,
) {
    fun getTextureById(id: Long): Texture? = repository.findByIdWithFile(id)
    fun getTextureByHash(hash: String): Texture? = repository.findByHash(hash)
    fun getTexturesByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Texture> =
        repository.findAllByUploaderIdAndPublicTexture(uploaderId, publicTexture)
    
    fun save(texture: Texture): Texture = repository.save(texture)
    fun saveAll(textures: Iterable<Texture>): List<Texture> = repository.saveAll(textures)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
    fun deleteAll(textures: Iterable<Texture>) = repository.deleteAll(textures)
    
    /** 管理端皮肤详情；披风由 [CapeService] 承载，不在此端点暴露。 */
    fun getSkinById(id: Long): Texture? = repository.findByIdWithFile(id)?.takeIf { it.type != Cape.TYPE }
    
    /** 管理端皮肤列表，披风已被仓储层排除。 */
    fun search(keyword: String, uploaderId: Long?, publicTexture: Boolean?, pageable: Pageable): Page<Texture> =
        repository.search(keyword, uploaderId, publicTexture, pageable)
    
    /** 上传者名下的全部皮肤，不含披风。 */
    fun getSkinsByUploader(uploaderId: Long): List<Texture> =
        repository.findAllByUploaderIdAndTypeNot(uploaderId, Cape.TYPE)
    
    /** 切换皮肤的公开状态；目标不存在或是披风时返回 null。 */
    fun setPublic(id: Long, publicTexture: Boolean): Texture? {
        val texture = getSkinById(id) ?: return null
        texture.publicTexture = publicTexture
        return repository.save(texture)
    }
    
    /** 按主键批量取材质，用于把角色绑定的材质主键解析成可渲染的哈希。 */
    fun getTexturesByIds(ids: Collection<Long>): List<Texture> =
        if (ids.isEmpty()) emptyList() else repository.findAllById(ids)
    
    /** 皮肤库列表：公开材质，可按类型与名称筛选。 */
    fun searchPublic(keyword: String, kind: String, pageable: Pageable): Page<Texture> =
        repository.searchPublic(keyword, kind, pageable)
    
    /**
     * 上传一份新材质。
     *
     * 校验 PNG 尺寸后交给 [FileService] 落盘，再按文件哈希去重：同一份内容重复上传时直接复用已有记录，
     * 与 Blessing Skin 一致——避免同一张皮肤在库里有多个条目。
     */
    fun createFromUpload(
        uploaderId: Long,
        name: String,
        type: String,
        publicTexture: Boolean,
        file: MultipartFile,
    ): Texture {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) throw ParamErrorException("材质名称不能为空")
        if (trimmedName.length > TEXTURE_NAME_MAX_LENGTH) {
            throw ParamErrorException("材质名称不能超过 $TEXTURE_NAME_MAX_LENGTH 个字符")
        }
        if (type !in SKIN_TYPES) throw ParamErrorException("材质类型只能是 steve、alex 或 cape")
        
        val size = readPngSize(file)
        if (size !in SUPPORTED_PNG_SIZES) {
            throw ParamErrorException("仅支持 64x64 或 64x32 的 PNG，当前为 ${size.first}x${size.second}")
        }
        
        val stored = fileService.upload(uploaderId, listOf(file)).single().file
        repository.findByHash(stored.sha256)?.let { return it }
        
        return repository.save(
            Texture(
                file = stored,
                name = trimmedName,
                type = type,
                hash = stored.sha256,
                size = stored.sizeBytes,
                uploaderId = uploaderId,
                publicTexture = publicTexture,
                uploadAt = LocalDateTime.now(),
            )
        )
    }
    
    /** 只读 PNG 头部的 IHDR 取宽高，避免为了校验尺寸把整张图读进内存。 */
    private fun readPngSize(file: MultipartFile): Pair<Int, Int> {
        val header = file.inputStream.use { it.readNBytes(PNG_HEADER_BYTES) }
        if (header.size < PNG_HEADER_BYTES || !header.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            throw ParamErrorException("文件不是合法的 PNG 图片")
        }
        val width = header.copyOfRange(16, 20).fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val height = header.copyOfRange(20, 24).fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        return width to height
    }
    
    private companion object {
        /** 校验尺寸需要读到的 PNG 头长度：签名 8 字节 + 块头 8 字节 + 宽高 8 字节。 */
        const val PNG_HEADER_BYTES = 24
        
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        
        val SUPPORTED_PNG_SIZES = setOf(64 to 64, 64 to 32)
        val SKIN_TYPES = setOf("steve", "alex", "cape")
        
        /** textures.name 列长度上限。 */
        const val TEXTURE_NAME_MAX_LENGTH = 50
    }
}
