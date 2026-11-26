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
 * 车次配载明细，对应 biz_trip_waybill（V29）。
 * 一行 = 一个配载单元：整票母单或拆单子单；金额为配载时快照。
 */
@Data
@TableName("biz_trip_waybill")
public class BizTripWaybill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tripId;

    private Long tripDestId;

    private String subTripNo;

    private Long waybillId;

    private String waybillNo;

    private String parentNo;

    /** 0=整票 1=子单 */
    private Integer isChild;

    private BigDecimal splitRatio;

    private Integer routeNodeSeq;

    private Long destHubId;

    private String destHubName;

    private String destName;

    private String customerName;

    private String receiverName;

    private String receiverPhone;

    private String goodsName;

    private Integer quantity;

    private BigDecimal weight;

    private BigDecimal volume;

    private BigDecimal prepaidFee;

    private BigDecimal collectFee;

    private BigDecimal returnFee;

    private BigDecimal monthlyFee;

    private BigDecimal totalFee;

    private BigDecimal rebateTotal;

    private BigDecimal validFreight;

    private BigDecimal allocatedFee;

    private BigDecimal profitCost;

    private BigDecimal grossProfit;

    private Integer statusBeforeLoad;

    /** 1=已配载 2=已发车 3=已到站 4=已卸车 */
    private Integer loadStatus;

    private LocalDateTime loadTime;

    private Long loadBy;

    private String loadByName;

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
