package com.yunshu.modules.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-额外可登录机构实体，对应 sys_user_org；复合主键(user_id, org_id)。
 * 只存额外机构，主机构由 sys_user.dept_id 表达。
 */
@Data
@TableName("sys_user_org")
public class SysUserOrg {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Long orgId;

    private Long createBy;

    private LocalDateTime createTime;
}
