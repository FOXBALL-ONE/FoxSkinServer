package top.foxball.foxskinserver.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.foxskinserver.entity.jdbc.Texture

/** 用于把角色绑定的纹理主键解析为材质哈希和文件信息。 */
interface TextureRepository : JpaRepository<Texture, Long> {
    @EntityGraph(attributePaths = ["file"])
    fun findByHash(hash: String): Texture?

    /** Profile 生成发生在事务外时也必须预加载关联文件，避免 open-in-view 关闭后的懒加载异常。 */
    @EntityGraph(attributePaths = ["file"])
    @Query("select t from Texture t where t.id = :id")
    fun findByIdWithFile(@Param("id") id: Long): Texture?

    fun findAllByUploaderIdAndPublicTexture(uploaderId: Long, publicTexture: Boolean): List<Texture>
}
