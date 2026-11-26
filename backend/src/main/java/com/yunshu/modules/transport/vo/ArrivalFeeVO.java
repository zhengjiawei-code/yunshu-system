package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 到站费用保存/预览（#36/#36a）：两项金额 + 本子车次非大车直送单元分摊。 */
@Data
public class ArrivalFeeVO {

    private Long tripId;

    private Long destId;

    private BigDecimal unloadFee;

    private BigDecimal otherFee;

    private BigDecimal total;

    private Boolean unloadFeeValid;

    private Boolean otherFeeValid;

    private Integer allocMethod;

    private String allocMethodName;

    private List<Unit> units;

    @Data
    public static class Unit {
        private Long tripWaybillId;
        private String waybillNo;
        private BigDecimal weight;
        private BigDecimal volume;
        private BigDecimal chargeWeight;
        private BigDecimal basis;
        private BigDecimal ratio;
        /** feeCode → 本票分摊金额 */
        private Map<String, BigDecimal> fees;
        private BigDecimal allocatedFee;
    }
}
