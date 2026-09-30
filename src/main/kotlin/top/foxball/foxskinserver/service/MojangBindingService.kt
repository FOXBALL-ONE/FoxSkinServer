package top.foxball.foxskinserver.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.foxskinserver.entity.jdbc.MojangIdentityBinding
import top.foxball.foxskinserver.handler.ParamErrorException
import top.foxball.foxskinserver.handler.ResourceNotFoundException
import top.foxball.foxskinserver.repository.MojangIdentityBindingRepository
import top.foxball.foxskinserver.repository.PlayerRepository
import top.foxball.foxskinserver.security.YggdrasilTokenStore
import java.time.LocalDateTime

@Service
class MojangBindingService(
    private val repository: MojangIdentityBindingRepository,
    private val players: PlayerRepository,
    private val tokenStore: YggdrasilTokenStore,
) {
    fun list(userId: Long): List<MojangIdentityBinding> =
        repository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, MojangIdentityBinding.ACTIVE)

    @Transactional
    fun bind(userId: Long, playerId: Long, profile: VerifiedMojangProfile): MojangIdentityBinding {
        val player = players.findById(playerId).orElse(null)
            ?: throw ResourceNotFoundException("角色不存在")
        if (player.userId != userId) throw ResourceNotFoundException("角色不存在")
        val uuidHistory = repository.findAllByMojangUuidOrderByUpdatedAtDesc(profile.id)
        if (uuidHistory.any { it.status == MojangIdentityBinding.ACTIVE }) {
            throw ParamErrorException("该正版账号已绑定其他角色")
        }
        val playerHistory = repository.findAllByPlayerIdOrderByUpdatedAtDesc(playerId)
        if (playerHistory.any { it.status == MojangIdentityBinding.ACTIVE }) {
            throw ParamErrorException("该角色已绑定正版账号，请先解绑")
        }
        val now = LocalDateTime.now()
        return try {
            repository.save(
                MojangIdentityBinding(
                    userId = userId,
                    playerId = playerId,
                    mojangUuid = profile.id,
                    mojangName = profile.name,
                    verifiedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw ParamErrorException("该正版账号或角色已被绑定")
        }
    }

    @Transactional
    fun unbind(userId: Long, bindingId: Long) {
        val binding = repository.findByIdAndUserId(bindingId, userId)
            ?: throw ResourceNotFoundException("正版绑定不存在")
        if (binding.status != MojangIdentityBinding.ACTIVE) throw ParamErrorException("正版绑定已解除")
        binding.status = MojangIdentityBinding.REVOKED
        binding.updatedAt = LocalDateTime.now()
        repository.save(binding)
    }

    @Transactional
    fun refreshBindingForProfile(profile: VerifiedMojangProfile): MojangIdentityBinding? {
        val binding = repository.findAllByMojangUuidAndStatus(profile.id, MojangIdentityBinding.ACTIVE).firstOrNull() ?: return null
        if (binding.mojangName != profile.name) {
            binding.mojangName = profile.name
            binding.verifiedAt = LocalDateTime.now()
            binding.updatedAt = binding.verifiedAt
            repository.save(binding)
        }
        return binding
    }

    fun bindingForProfile(profile: VerifiedMojangProfile): MojangIdentityBinding? =
        repository.findAllByMojangUuidAndStatus(profile.id, MojangIdentityBinding.ACTIVE).firstOrNull()
}
