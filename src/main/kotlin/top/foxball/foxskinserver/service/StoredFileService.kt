package top.foxball.foxskinserver.service

import org.springframework.stereotype.Service
import top.foxball.foxskinserver.entity.jdbc.StoredFile
import top.foxball.foxskinserver.repository.StoredFileRepository
import java.util.UUID

@Service
class StoredFileService(private val repository: StoredFileRepository) {
    fun getStoredFileById(id: UUID): StoredFile? = repository.findById(id).orElse(null)
    fun save(file: StoredFile): StoredFile = repository.save(file)
    fun saveAll(files: Iterable<StoredFile>): List<StoredFile> = repository.saveAll(files)
    fun deleteById(id: UUID) = repository.deleteById(id)
    fun deleteAllById(ids: Iterable<UUID>) = repository.deleteAllById(ids)
}
