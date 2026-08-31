package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.foxskinserver.entity.jdbc.LanguageLine

/** 翻译文本数据访问仓储。 */
interface LanguageLineRepository : JpaRepository<LanguageLine, Long> {
    fun findLanguageLineByGroupAndKey(group: String, key: String): LanguageLine?
}
