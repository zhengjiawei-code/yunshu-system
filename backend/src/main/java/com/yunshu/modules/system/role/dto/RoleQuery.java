package com.yunshu.modules.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色列表查询参数：筛选、排序与分页均走服务端。
 */
@Data
@Schema(description = "角色列表查询参数")
public class RoleQuery {

    @Schema(description = "角色名称，模糊匹配")
    private String roleName;

    @Schema(description = "角色编码，模糊匹配")
    private String roleCode;

    @Schema(description = "角色描述，模糊匹配")
    private String roleDesc;

    @Schema(description = "最后修改人姓名，模糊匹配（依赖 sys_user；表不存在时忽略）")
    private String updateByName;

    @Schema(description = "最后修改时间起，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String updateTimeStart;

    @Schema(description = "最后修改时间止，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String updateTimeEnd;

    @Schema(description = "排序字段：roleName/roleCode/roleDesc/updateByName/updateTime/status/id")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc")
    private String sortOrder;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认 20")
    private Integer pageSize = 20;
}
