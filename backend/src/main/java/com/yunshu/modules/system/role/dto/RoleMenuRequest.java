package com.yunshu.modules.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 角色菜单授权请求：提交 checked + halfChecked 的并集，整体覆盖保存。
 */
@Data
@Schema(description = "角色菜单授权请求")
public class RoleMenuRequest {

    @Schema(description = "菜单/按钮ID列表，整体覆盖")
    private List<Long> menuIds;
}
