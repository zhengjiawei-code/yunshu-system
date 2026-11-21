package com.yunshu.modules.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/编辑参数。用户名创建后不可修改；密码只在新增加密落库，编辑不出现。
 */
@Data
@Schema(description = "用户新增/编辑参数")
public class UserSaveRequest {

    @Schema(description = "登录账号：2-20 位字母/数字/下划线，创建后不可修改")
    @NotBlank(message = "请输入用户名")
    @Pattern(regexp = "^\\w{2,20}$", message = "用户名需为 2-20 位字母、数字或下划线")
    private String username;

    @Schema(description = "密码：新增必填，6-20 位；编辑忽略")
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    @Schema(description = "真实姓名：2-20 个字符")
    @NotBlank(message = "请输入真实姓名")
    @Size(min = 2, max = 20, message = "真实姓名长度需为 2-20 个字符")
    private String realName;

    @Schema(description = "手机号：必填，1[3-9] 开头 11 位；格式由 Service 二次校验")
    @NotBlank(message = "请输入手机号")
    private String phone;

    @Schema(description = "邮箱：选填")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String email;

    @Schema(description = "用户类型：1=内部员工 2=客户")
    @NotNull(message = "请选择用户类型")
    private Integer userType;

    @Schema(description = "所属主机构：内部员工必填；客户可空")
    private Long deptId;

    @Schema(description = "可登录机构ID列表（不含主机构，主机构自动包含）")
    private List<Long> authOrgIds;

    @Schema(description = "角色ID列表：必填，至少 1 个")
    @NotEmpty(message = "请至少选择 1 个角色")
    private List<Long> roleIds;

    @Schema(description = "状态：true 启用 / false 禁用")
    private Boolean status;

    @Schema(description = "数据范围：1/3/4/5，默认 4；客户不适用")
    private Integer dataScope;

    @Schema(description = "备注：最多 500 字符")
    @Size(max = 500, message = "备注最长 500 个字符")
    private String remark;
}
