package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 费用分摊结果（#11 保存返回 / #12 预览返回）。
 * items 为每项费用总额，units 为逐配载单元的分摊明细，summary 为车次级汇总。
 */
@Data
public class FeePreviewVO {

    private Long tripId;

    private Integer allocMethod;

    private String allocMethodName;

    /** 当前分摊方式下全车基数合计 */
    private BigDecimal basisTotal;

    private List<ItemTotal> items;

    private List<Unit> units;

    private Summary summary;

    @Data
    public static class ItemTotal {
        private String feeCode;
        private String feeName;
        private BigDecimal amount;
        private Boolean profitEnabled;
    }

    @Data
    public static class Unit {
        private Long tripWaybillId;
        private Long waybillId;
        private String waybillNo;
        private String parentNo;
        private Integer isChild;
        private Integer quantity;
        private BigDecimal weight;
        private BigDecimal volume;
        /** 重泡比计费重量 = MAX(重量, 体积 × 系数) */
        private BigDecimal chargeWeight;
        /** 本票所用重泡比系数（方式 3 展示用） */
        private BigDecimal volumeWeightRatio;
        private BigDecimal basis;
        private BigDecimal ratio;
        /** feeCode → 本票分摊金额 */
        private Map<String, BigDecimal> fees;
        private BigDecimal allocatedFee;
        private BigDecimal profitCost;
    }

    @Data
    public static class Summary {
        private Integer unitCount;
        private BigDecimal incomeTotal;
        private BigDecimal rebateTotal;
        private BigDecimal costTotal;
        private BigDecimal grossProfit;
    }
}
