package top.foxball.foxskinserver

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 管理端接口的访问控制：/api/admin 下的接口只对管理员角色开放。 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAccessControlTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    /** 未携带令牌时由过滤器链拒绝。 */
    @Test
    fun unauthenticatedRequestIsRejected() {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized)
    }

    /** 普通用户即使已登录也不能进入管理端，验证类级 @PreAuthorize 生效。 */
    @Test
    @WithMockUser(roles = ["USER"])
    fun normalUserIsForbidden() {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden)
    }

    /** 管理员可以列出用户，且列表沿用统一的 list + pagination 响应结构。 */
    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun adminIsAllowed() {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.list").isArray)
            .andExpect(jsonPath("$.data.pagination.page").value(1))
            .andExpect(jsonPath("$.data.pagination.total").exists())
    }
}
