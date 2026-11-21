package com.yunshu.modules.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增/编辑载荷，字段与前端 RoleSavePayload 一一对应。
 */
@Data
@Schema(description = "角色保存请求")
public class RoleSaveRequest {

    @NotBlank(message = "请输入角色名称")
    @Size(min = 2, max = 20, message = "角色名称为 2-20 个字符")
    @Schema(description = "角色名称，全局唯一")
    private String roleName;

    @NotBlank(message = "请输入角色编码")
    @Pattern(regexp = "^\\w{2,20}$", message = "角色编码为 2-20 位字母、数字或下划线")
    @Schema(description = "角色编码，全局唯一；创建后不可修改")
    private String roleCode;

    @Size(max = 200, message = "角色描述不能超过 200 个字符")
    @Schema(description = "角色描述")
    private String roleDesc;

    @Schema(description = "状态：true=启用 false=禁用；admin 不可禁用")
    private Boolean status;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    @Schema(description = "备注")
    private String remark;
}
