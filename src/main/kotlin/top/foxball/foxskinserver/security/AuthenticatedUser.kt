package top.foxball.foxskinserver.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import top.foxball.foxskinserver.entity.jdbc.User

/** 放入 SecurityContext 的当前用户，保留业务用户主键及权限等级。 */
data class AuthenticatedUser(
    val userId: Long,
    val email: String,
    val loginUsername: String,
    val nickname: String,
    val permission: Int,
    val verified: Boolean,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = buildList {
        add(SimpleGrantedAuthority("ROLE_USER"))
        if (permission >= User.ADMIN) add(SimpleGrantedAuthority("ROLE_ADMIN"))
        if (permission >= User.SUPER_ADMIN) add(SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
    }

    override fun getPassword(): String = ""
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = permission != User.BANNED
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = permission != User.BANNED

    companion object {
        fun from(user: User): AuthenticatedUser = AuthenticatedUser(
            userId = requireNotNull(user.id),
            email = user.email,
            loginUsername = user.username,
            nickname = user.nickname,
            permission = user.permission,
            verified = user.verified,
        )
    }
}
