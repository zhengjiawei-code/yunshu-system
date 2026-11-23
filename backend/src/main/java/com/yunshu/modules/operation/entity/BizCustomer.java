package com.yunshu.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 简易客户表，对应 biz_customer；开单只读匹配，不在开单页新建 */
@Data
@TableName("biz_customer")
public class BizCustomer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String customerName;

    private String contactPhone;

    private String address;

    /** 1=启用 0=停用 */
    private Integer status;

    private String remark;

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
