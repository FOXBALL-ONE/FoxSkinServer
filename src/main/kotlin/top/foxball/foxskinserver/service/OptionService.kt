package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.Option
import top.foxball.foxskinserver.repository.OptionRepository

@Service
class OptionService(private val repository: OptionRepository) {
    fun getOptionById(id: Long): Option? = repository.findById(id).orElse(null)
    fun getOptionByName(name: String): Option? = repository.findOptionByName(name)
    fun save(option: Option): Option = repository.save(option)
    fun saveAll(options: Iterable<Option>): List<Option> = repository.saveAll(options)
    fun deleteById(id: Long) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<Long>) = repository.deleteAllById(ids)
}
