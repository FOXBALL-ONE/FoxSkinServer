package top.foxball.foxskinserver.entity.jdbc

import jakarta.persistence.*

/** OAuth 授权作用域实体。 */
@Entity
@Table(name = "scopes")
class Scope(
    /** 作用域主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    /** 作用域名称。 */
    @Column(nullable = false, unique = true)
    var name: String = "",
    
    /** 作用域描述。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",
)
