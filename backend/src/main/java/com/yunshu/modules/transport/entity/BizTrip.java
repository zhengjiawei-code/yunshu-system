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
 * 车次主表，对应 biz_trip（V28）。
 * 汇总字段全部由服务端计算，客户端传值不落库。
 */
@Data
@TableName("biz_trip")
public class BizTrip {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tripNo;

    private String tripName;

    /** 1=干线 2=短驳；本期只写 1 */
    private Integer tripCategory;

    /** 10计划 20配载 30在途 40部分到站 50到站 60完成卸车 90关闭 */
    private Integer tripStatus;

    private Long originDeptId;

    private String originDeptName;

    private LocalDateTime planDepartTime;

    private LocalDateTime departTime;

    private LocalDateTime planArriveTime;

    private LocalDateTime actualArriveTime;

    private Long vehicleId;

    private String plateNo;

    private String vehicleType;

    private BigDecimal vehicleLoad;

    private BigDecimal vehicleVolume;

    private Long driverId;

    private String driverName;

    private String driverPhone;

    private Integer totalWaybillCount;

    private Integer totalQuantity;

    private BigDecimal totalWeight;

    private BigDecimal totalVolume;

    private BigDecimal loadRateWeight;

    private BigDecimal loadRateVolume;

    private BigDecimal totalPrepaidFreight;

    private BigDecimal totalCollectFreight;

    private BigDecimal totalReturnFreight;

    private BigDecimal totalMonthlyFreight;

    private BigDecimal totalFreight;

    private BigDecimal totalRebate;

    private BigDecimal totalValidFreight;

    private BigDecimal totalPickupCost;

    private BigDecimal totalDeliveryFee;

    private BigDecimal totalTransferFee;

    /** 1=重量 2=体积 3=重泡比计费重量 */
    private Integer allocMethod;

    private BigDecimal prepaidTransportFee;

    private BigDecimal returnTransportFee;

    private BigDecimal arrivalTransportFee;

    private BigDecimal prepaidOilCardFee;

    private BigDecimal wholeWeighingFee;

    private BigDecimal wholeInsuranceFee;

    private BigDecimal bulkLoadFee;

    private BigDecimal bulkUnloadFee;

    private BigDecimal bulkOtherFee;

    private BigDecimal arrivalUnloadFee;

    private BigDecimal arrivalOtherFee;

    private BigDecimal totalCostFee;

    private BigDecimal grossProfit;

    private Long departBy;

    private String departByName;

    private String closeReason;

    private LocalDateTime closeTime;

    private Long closeBy;

    private String closeByName;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private String createByName;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    private String updateByName;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
