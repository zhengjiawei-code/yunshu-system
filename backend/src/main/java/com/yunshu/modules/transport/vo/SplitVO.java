package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 拆单预览/拆单结果：子单与母单余额金额、重量、体积一键核对。 */
@Data
public class SplitVO {

    private Long parentWaybillId;
    private String parentWaybillNo;
    private Integer originalQuantity;
    private BigDecimal originalWeight;
    private BigDecimal originalVolume;
    private Integer remainingQuantityBefore;
    private BigDecimal remainingWeightBefore;
    private BigDecimal remainingVolumeBefore;
    private Integer splitQuantity;
    private BigDecimal splitRatio;
    private Amounts child;
    private Amounts parentRemaining;

    /** 拆单成功后才返回 */
    private Long childWaybillId;
    private String childWaybillNo;
    private Integer childSeq;
    private Integer childStatus;
    private Integer routeNodeCount;

    @Data
    public static class Amounts {
        private Integer quantity;
        private BigDecimal weight;
        private BigDecimal volume;
        private BigDecimal prepaidFee;
        private BigDecimal collectFee;
        private BigDecimal returnFee;
        private BigDecimal monthlyFee;
        private BigDecimal deliveryFee;
        private BigDecimal totalFee;
        private BigDecimal cashBack;
        private BigDecimal oweBack;
        private BigDecimal rebateTotal;
        private BigDecimal validFreight;
    }
}
