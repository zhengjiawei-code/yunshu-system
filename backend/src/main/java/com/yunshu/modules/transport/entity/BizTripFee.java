package com.yunshu.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车次费用明细，对应 biz_trip_fee（V30）。
 * 只存车次录入的成本项；四类运费收入不进本表。
 */
@Data
@TableName("biz_trip_fee")
public class BizTripFee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tripId;

    /** 0=车次级；>0=目的网点级（到站卸车费/其他费） */
    private Long tripDestId;

    private String feeCode;

    private String feeName;

    private Integer feeCategory;

    private BigDecimal amount;

    private Boolean profitEnabled;

    private Integer profitDirection;

    /** 1=有效 2=已失效 */
    private Integer feeStatus;

    private String invalidReason;

    private Integer sortOrder;

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
