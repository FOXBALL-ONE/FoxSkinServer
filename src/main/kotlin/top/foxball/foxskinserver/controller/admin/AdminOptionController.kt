package top.foxball.foxskinserver.controller.admin

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.foxskinserver.service.OptionService
import top.foxball.foxskinserver.shared.Response
import top.foxball.foxskinserver.shared.ResponseBuilder

/**
 * 管理端站点配置接口，仅管理员可访问。
 *
 * @folder 管理端/站点配置
 */
@Validated
@RestController
@RequestMapping("/api/admin/options")
@PreAuthorize("hasRole('ADMIN')")
class AdminOptionController(
    private val optionService: OptionService,
    private val builder: ResponseBuilder,
) {
    /**
     * @api 配置项列表
     * @param page 分页页码
     * @param pageSize 分页每页数量
     */
    @GetMapping
    fun listOptions(
        @RequestParam("page", defaultValue = "1") @Min(1) page: Int,
        @RequestParam("size", defaultValue = "25") @Min(1) @Max(100) pageSize: Int,
    ): ResponseEntity<Response> {
        data class OptionData(
            val id: Long,
            val name: String,
            val value: String,
        )
        
        data class Pagination(
            val page: Int,
            val size: Int,
            val total: Long,
            @param:JsonProperty("total_pages")
            val totalPages: Int,
        )
        
        data class Response(
            val list: List<OptionData>,
            val pagination: Pagination,
        )
        
        val paged = optionService.search(
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "id")),
        )
        val list = paged.content.map { option ->
            OptionData(
                id = requireNotNull(option.id),
                name = option.name,
                value = option.value,
            )
        }
        val rs = Response(
            list,
            Pagination(paged.number + 1, paged.size, paged.totalElements, paged.totalPages),
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 配置项详情
     * @param optionName 配置项名称
     */
    @GetMapping("/{option_name}")
    fun getOption(@PathVariable("option_name") optionName: String): ResponseEntity<Response> {
        data class OptionData(
            val id: Long,
            val name: String,
            val value: String,
        )
        
        val option = optionService.getOptionByName(optionName)
            ?: return builder.notFound().build()
        val rs = OptionData(
            id = requireNotNull(option.id),
            name = option.name,
            value = option.value,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 写入配置项，不存在时新建
     * @param optionName 配置项名称
     * @param value 配置项内容
     */
    @PutMapping("/{option_name}")
    fun setOption(
        @PathVariable("option_name") optionName: String,
        @RequestParam("value") value: String,
    ): ResponseEntity<Response> {
        data class OptionData(
            val id: Long,
            val name: String,
            val value: String,
        )
        
        val option = optionService.setValue(optionName, value)
        val rs = OptionData(
            id = requireNotNull(option.id),
            name = option.name,
            value = option.value,
        )
        return builder.ok()
            .data(rs)
            .build()
    }
    
    /**
     * @api 删除配置项
     * @param optionName 配置项名称
     */
    @DeleteMapping("/{option_name}")
    fun deleteOption(@PathVariable("option_name") optionName: String): ResponseEntity<Response> {
        data class Response(
            val name: String,
            val deleted: Boolean,
        )
        
        val deleted = optionService.deleteByName(optionName)
        if (!deleted) return builder.notFound().build()
        val rs = Response(optionName, true)
        return builder.ok()
            .data(rs)
            .build()
    }
}
