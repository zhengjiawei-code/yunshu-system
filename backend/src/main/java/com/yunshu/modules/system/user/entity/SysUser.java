package com.yunshu.modules.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体，对应 sys_user。
 * 角色决定功能权限；data_scope 决定数据范围（默认 4，过滤开关默认关闭）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，创建后不可修改，deleted=0 范围唯一 */
    private String username;

    /** BCrypt 密文，禁止存明文 */
    private String password;

    private String realName;

    private String phone;

    private String email;

    /** 所属主机构；内部员工必填，客户可空 */
    private Long deptId;

    /** 1=启用 0=禁用 */
    private Boolean status;

    /** 1=内部员工 2=客户 */
    private Integer userType;

    /** 1=全部 3=本机构 4=本机构及以下 5=仅本人；2=自定义机构预留未实现 */
    private Integer dataScope;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
