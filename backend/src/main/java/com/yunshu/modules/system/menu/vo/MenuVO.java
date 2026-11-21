package com.yunshu.modules.system.menu.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单树节点，字段名与前端 MenuNode 类型对齐。
 */
@Data
@Schema(description = "菜单树节点")
public class MenuVO {

    private Long id;

    private Long parentId;

    private String menuName;

    private Integer menuType;

    private String routePath;

    private String component;

    private String redirect;

    private String perms;

    private String icon;

    private String terminalType;

    private Integer sortOrder;

    private Boolean visible;

    private Boolean status;

    private Boolean keepAlive;

    private Boolean affix;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private List<MenuVO> children;
}
