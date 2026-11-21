package com.yunshu.modules.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 直授弹窗回显：角色已含 / 额外授权 / 显式禁止。
 */
@Data
@Schema(description = "用户直授回显")
public class UserPermVO {

    @Schema(description = "角色已含的菜单ID（前端置灰）")
    private List<Long> roleMenuIds;

    @Schema(description = "额外授权菜单ID")
    private List<Long> grantedIds;

    @Schema(description = "显式禁止菜单ID")
    private List<Long> deniedIds;
}
