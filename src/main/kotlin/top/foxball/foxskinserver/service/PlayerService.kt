package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Player
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.ResourceNotFoundException
import top.foxball.foxskinserver.repository.PlayerRepository
import java.time.LocalDateTime
import java.util.UUID

/** 角色连同它当前绑定的皮肤/披风哈希，供前端直接渲染预览图。 */
data class PlayerWithTextures(
    val player: Player,
    val skinHash: String?,
    val capeHash: String?,
)

@Service
class PlayerService(
    private val repository: PlayerRepository,
    private val textureService: TextureService,
) {
    fun getPlayerById(id: Long): Player? = repository.findById(id).orElse(null)
    fun getPlayerByUuid(uuid: UUID): Player? = repository.findByUuid(uuid)
    fun getPlayerByName(name: String): Player? = repository.findByName(name)
    fun getPlayersByUserId(userId: Long): List<Player> = repository.findAllByUserId(userId)
    fun getPlayerByUserIdAndName(userId: Long, name: String): Player? =
        repository.findPlayerByUserIdAndName(userId, name)
    
    fun save(player: Player): Player = repository.save(player)
    fun saveAll(players: Iterable<Player>): List<Player> = repository.saveAll(players)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
    fun deleteAll(players: Iterable<Player>) = repository.deleteAll(players)
    
    /** 角色列表，附带已绑定材质的哈希；用两次查询换取列表接口不触发逐条懒加载。 */
    fun getPlayersWithTextures(userId: Long): List<PlayerWithTextures> {
        val players = repository.findAllByUserId(userId)
        val textureIds = players.flatMap { listOf(it.skinTextureId, it.capeTextureId) }.filter { it > 0 }.distinct()
        val hashes = textureService.getTexturesByIds(textureIds).associate { requireNotNull(it.id) to it.hash }
        return players.map { player ->
            PlayerWithTextures(
                player = player,
                skinHash = hashes[player.skinTextureId],
                capeHash = hashes[player.capeTextureId],
            )
        }
    }
    
    /** 新建角色。名称需符合 Minecraft 规则且全局不重名。 */
    fun createPlayer(userId: Long, name: String): Player {
        val normalized = requireValidName(name)
        requireNameAvailable(normalized, excludePlayerId = null)
        return repository.save(
            Player(
                userId = userId,
                uuid = UUID.randomUUID(),
                name = normalized,
                lastModified = LocalDateTime.now(),
            )
        )
    }
    
    /** 重命名自己的角色；角色不存在或不属于该用户时抛 404。 */
    fun renamePlayer(userId: Long, playerId: Long, name: String): Player {
        val player = requireOwnedPlayer(userId, playerId)
        val normalized = requireValidName(name)
        requireNameAvailable(normalized, excludePlayerId = playerId)
        player.name = normalized
        player.lastModified = LocalDateTime.now()
        return repository.save(player)
    }
    
    /**
     * 为角色设置皮肤/披风。
     *
     * 传 null 表示保持原绑定；`skinTextureId` 传 [NO_TEXTURE]（-1）或 `capeTextureId` 传 0 表示解除绑定。
     * 只允许绑定自己上传的、或已公开的材质，避免把别人的私有皮肤挂到自己角色上。
     */
    fun setPlayerTextures(
        userId: Long,
        playerId: Long,
        skinTextureId: Long?,
        capeTextureId: Long?,
    ): Player {
        val player = requireOwnedPlayer(userId, playerId)
        skinTextureId?.let { player.skinTextureId = requireUsableTexture(userId, it, SKIN_TYPE) }
        capeTextureId?.let { player.capeTextureId = requireUsableTexture(userId, it, CAPE_TYPE) }
        player.lastModified = LocalDateTime.now()
        return repository.save(player)
    }
    
    /** 删除自己的角色；角色不存在或不属于该用户时抛 404。 */
    fun deletePlayer(userId: Long, playerId: Long) {
        repository.delete(requireOwnedPlayer(userId, playerId))
    }
    
    private fun requireOwnedPlayer(userId: Long, playerId: Long): Player {
        val player = repository.findById(playerId).orElse(null)
        if (player == null || player.userId != userId) throw ResourceNotFoundException("角色不存在")
        return player
    }
    
    private fun requireValidName(name: String): String {
        val trimmed = name.trim()
        if (!PLAYER_NAME_PATTERN.matches(trimmed)) {
            throw ParamErrorException("角色名只能包含字母、数字和下划线，长度 3-16 位")
        }
        return trimmed
    }
    
    private fun requireNameAvailable(name: String, excludePlayerId: Long?) {
        val taken = repository.findAllByNameIgnoreCase(name).any { it.id != excludePlayerId }
        if (taken) throw ParamErrorException("角色名 $name 已被占用")
    }
    
    /** 返回校验通过的材质主键；传该字段的“未绑定”哨兵值（皮肤 -1、披风 0）表示解除绑定。 */
    private fun requireUsableTexture(userId: Long, textureId: Long, kind: String): Long {
        val unboundValue = if (kind == CAPE_TYPE) 0L else NO_TEXTURE_ID
        if (textureId == unboundValue) return unboundValue
        val texture = textureService.getTextureById(textureId) ?: throw ResourceNotFoundException("材质不存在")
        if ((kind == CAPE_TYPE) != (texture.type == CAPE_TYPE)) {
            throw ParamErrorException("材质类型与绑定位置不匹配")
        }
        if (texture.uploaderId != userId && !texture.publicTexture) {
            throw ResourceNotFoundException("材质不存在")
        }
        return textureId
    }
    
    private companion object {
        /** 与 Minecraft 一致：字母、数字、下划线，长度 3-16 位。 */
        val PLAYER_NAME_PATTERN = Regex("^[A-Za-z0-9_]{3,16}$")
        
        /** players.tid_skin 用 -1 表示未绑定。 */
        const val NO_TEXTURE_ID = -1L
        
        /** 绑定位置的类型判别值，不是 textures.type 的取值。 */
        const val SKIN_TYPE = "skin"
        const val CAPE_TYPE = "cape"
    }
}
