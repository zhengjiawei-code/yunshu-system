package com.yunshu.modules.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号级菜单直授实体，对应 sys_user_menu。
 * grantType=1 额外授权；0 显式禁止（禁止级联子孙，且优先于授权）。
 */
@Data
@TableName("sys_user_menu")
public class SysUserMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long menuId;

    /** 1=额外授权 0=显式禁止 */
    private Integer grantType;

    private Long createBy;

    private LocalDateTime createTime;
}
