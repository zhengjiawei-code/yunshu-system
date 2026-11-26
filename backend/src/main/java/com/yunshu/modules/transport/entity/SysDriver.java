package com.yunshu.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 司机最小主数据，对应 sys_driver（V32）；车次选择与快速新增使用。
 */
@Data
@TableName("sys_driver")
public class SysDriver {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String driverName;

    private String driverPhone;

    private String licenseNo;

    /** true=启用 false=停用 */
    private Boolean status;

    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
