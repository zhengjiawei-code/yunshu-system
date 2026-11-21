package com.yunshu.modules.system.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色表实体，对应 sys_role。
 * 角色只承载菜单/按钮权限，不含任何数据范围语义；数据范围在 sys_user.data_scope。
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称，全局唯一 */
    private String roleName;

    /** 角色编码，全局唯一，创建后不可修改；admin 为内置超级管理员标识 */
    private String roleCode;

    private String roleDesc;

    /** 1=启用 0=禁用；admin 固定启用 */
    private Boolean status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
