package com.yunshu.modules.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体，对应 sys_user_role；复合主键(user_id, role_id)，保存时整体覆盖。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Long roleId;

    private Long createBy;

    private LocalDateTime createTime;
}
