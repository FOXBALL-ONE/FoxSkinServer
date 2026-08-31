package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Player
import top.foxball.foxskinserver.repository.PlayerRepository
import java.util.UUID

@Service
class PlayerService(private val repository: PlayerRepository) {
    fun getPlayerById(id: Long): Player? = repository.findById(id).orElse(null)
    fun getPlayerByUuid(uuid: UUID): Player? = repository.findByUuid(uuid)
    fun getPlayerByName(name: String): Player? = repository.findByName(name)
    fun getPlayersByUserId(userId: Long): List<Player> = repository.findAllByUserId(userId)
    fun getPlayerByUserIdAndName(userId: Long, name: String): Player? = repository.findPlayerByUserIdAndName(userId, name)
    fun save(player: Player): Player = repository.save(player)
    fun saveAll(players: Iterable<Player>): List<Player> = repository.saveAll(players)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
