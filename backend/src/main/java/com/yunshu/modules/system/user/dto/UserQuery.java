package com.yunshu.modules.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户列表查询参数：筛选、排序与分页均走服务端。
 */
@Data
@Schema(description = "用户列表查询参数")
public class UserQuery {

    @Schema(description = "用户名，模糊匹配")
    private String username;

    @Schema(description = "真实姓名，模糊匹配")
    private String realName;

    @Schema(description = "手机号，模糊匹配")
    private String phone;

    @Schema(description = "邮箱，模糊匹配")
    private String email;

    @Schema(description = "所属机构ID")
    private Long deptId;

    @Schema(description = "角色ID：筛选拥有该角色的用户")
    private Long roleId;

    @Schema(description = "是否有直接授权（额外授权或显式禁止）")
    private Boolean hasDirectPerm;

    @Schema(description = "状态：true 启用 / false 禁用")
    private Boolean status;

    @Schema(description = "最后修改人姓名，模糊匹配")
    private String updateByName;

    @Schema(description = "最后修改时间起，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String updateTimeStart;

    @Schema(description = "最后修改时间止，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String updateTimeEnd;

    @Schema(description = "排序字段：username/realName/phone/email/deptName/status/updateByName/updateTime/id")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc")
    private String sortOrder;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认 20")
    private Integer pageSize = 20;
}
