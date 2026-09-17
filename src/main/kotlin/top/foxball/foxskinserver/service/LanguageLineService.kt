package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.LanguageLine
import top.foxball.foxskinserver.repository.LanguageLineRepository

@Service
class LanguageLineService(private val repository: LanguageLineRepository) {
    fun getLanguageLineById(id: Long): LanguageLine? = repository.findById(id).orElse(null)
    fun getLanguageLineByGroupAndKey(group: String, key: String): LanguageLine? =
        repository.findLanguageLineByGroupAndKey(group, key)
    
    fun save(line: LanguageLine): LanguageLine = repository.save(line)
    fun saveAll(lines: Iterable<LanguageLine>): List<LanguageLine> = repository.saveAll(lines)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
