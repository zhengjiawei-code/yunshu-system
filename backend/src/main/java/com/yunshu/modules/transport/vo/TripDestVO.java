package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 子车次视图。 */
@Data
public class TripDestVO {

    private Long id;
    private Long tripId;
    private String subTripNo;
    private Integer seq;
    private Long destDeptId;
    private String destDeptName;
    private Integer subStatus;
    private LocalDateTime planArriveTime;
    private LocalDateTime arriveTime;
    private LocalDateTime unloadTime;
    private Integer totalWaybillCount;
    private Integer totalQuantity;
    private BigDecimal totalWeight;
    private BigDecimal totalVolume;
    private BigDecimal arrivalUnloadFee;
    private BigDecimal arrivalOtherFee;
    private Boolean arrivalUnloadFeeValid;
    private Boolean arrivalOtherFeeValid;
}
