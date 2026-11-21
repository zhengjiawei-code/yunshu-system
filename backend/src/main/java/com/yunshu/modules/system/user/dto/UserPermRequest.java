package com.yunshu.modules.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 账号级直授保存参数：整体覆盖，禁止优先（denied 会从 granted 中剔除）。
 */
@Data
@Schema(description = "用户直授保存参数")
public class UserPermRequest {

    @Schema(description = "额外授权菜单ID列表")
    private List<Long> grantedIds;

    @Schema(description = "显式禁止菜单ID列表（级联其全部子孙）")
    private List<Long> deniedIds;
}
