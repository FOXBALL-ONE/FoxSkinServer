package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Scope
import top.foxball.foxskinserver.repository.ScopeRepository

@Service
class ScopeService(private val repository: ScopeRepository) {
    fun getScopeById(id: Long): Scope? = repository.findById(id).orElse(null)
    fun getScopeByName(name: String): Scope? = repository.findScopeByName(name)
    fun save(scope: Scope): Scope = repository.save(scope)
    fun saveAll(scopes: Iterable<Scope>): List<Scope> = repository.saveAll(scopes)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
