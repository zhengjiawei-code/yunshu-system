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
 * 车次目的网点（子车次），对应 biz_trip_dest（V28）。
 * 一行 = 一个目的网点，承载独立的到站/卸车状态与到站费用。
 */
@Data
@TableName("biz_trip_dest")
public class BizTripDest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tripId;

    private String subTripNo;

    /** 目的网点顺序号，从 1 开始，同车次内唯一 */
    private Integer seq;

    private Long destDeptId;

    private String destDeptName;

    /** 10待发车 20在途 30已到站 40完成卸车 */
    private Integer subStatus;

    private LocalDateTime planArriveTime;

    private LocalDateTime arriveTime;

    private Long arriveBy;

    private String arriveByName;

    private LocalDateTime unloadTime;

    private Long unloadBy;

    private String unloadByName;

    private Integer totalWaybillCount;

    private Integer totalQuantity;

    private BigDecimal totalWeight;

    private BigDecimal totalVolume;

    private BigDecimal arrivalUnloadFee;

    private Boolean arrivalUnloadFeeValid;

    private BigDecimal arrivalOtherFee;

    private Boolean arrivalOtherFeeValid;

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
