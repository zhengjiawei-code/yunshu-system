package com.yunshu.modules.system.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单权限表实体，对应 sys_menu；目录、菜单、按钮共用一张表。
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级节点ID，0=顶级 */
    private Long parentId;

    private String menuName;

    /** 1=目录 2=菜单 3=按钮 */
    private Integer menuType;

    private String routePath;

    private String component;

    private String redirect;

    private String perms;

    private String icon;

    /** PC / APP / PC,APP */
    private String terminalType;

    private Integer sortOrder;

    private Boolean visible;

    private Boolean status;

    private Boolean keepAlive;

    private Boolean affix;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
