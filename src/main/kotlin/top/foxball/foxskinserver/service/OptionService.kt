package top.foxball.foxskinserver.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    
    /** 管理端配置项列表。 */
    fun search(pageable: Pageable): Page<Option> = repository.findAll(pageable)
    
    /** 按名称写入配置项，不存在时新建。 */
    fun setValue(name: String, value: String): Option {
        val option = repository.findOptionByName(name) ?: Option(name = name)
        option.value = value
        return repository.save(option)
    }
    
    /** 按名称删除配置项，返回是否确实删除了一条。 */
    fun deleteByName(name: String): Boolean {
        val option = repository.findOptionByName(name) ?: return false
        repository.delete(option)
        return true
    }
}
