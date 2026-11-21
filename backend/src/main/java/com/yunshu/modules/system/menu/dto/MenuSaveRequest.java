package com.yunshu.modules.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 菜单新增/编辑载荷，字段与前端 MenuSavePayload 一一对应。
 */
@Data
@Schema(description = "菜单保存请求")
public class MenuSaveRequest {

    @NotNull(message = "上级节点不能为空")
    @Schema(description = "上级节点ID，0=顶级")
    private Long parentId;

    @NotNull(message = "节点类型不能为空")
    @Min(value = 1, message = "节点类型只能是 1目录 2菜单 3按钮")
    @Max(value = 3, message = "节点类型只能是 1目录 2菜单 3按钮")
    private Integer menuType;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称不能超过 50 个字符")
    private String menuName;

    @Size(max = 200, message = "路由地址不能超过 200 个字符")
    private String routePath;

    @Size(max = 200, message = "组件路径不能超过 200 个字符")
    private String component;

    @Size(max = 200, message = "重定向地址不能超过 200 个字符")
    private String redirect;

    @Size(max = 100, message = "权限标识不能超过 100 个字符")
    private String perms;

    @Size(max = 64, message = "图标不能超过 64 个字符")
    private String icon;

    private String terminalType;

    private Integer sortOrder;

    private Boolean visible;

    private Boolean status;

    private Boolean keepAlive;

    private Boolean affix;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    @Schema(description = "拖拽排序用，保存接口忽略")
    private List<Long> ids;
}
