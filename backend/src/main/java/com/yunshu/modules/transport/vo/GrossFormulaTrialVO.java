package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 单票试算结果（#40）：逐项收入/返款/分摊成本 + 单票毛利；可选车次对账。 */
@Data
public class GrossFormulaTrialVO {

    private Long tripWaybillId;

    private Long waybillId;

    private String waybillNo;

    private List<Line> incomeItems;

    private BigDecimal rebate;

    private Boolean rebateDeduct;

    private List<Line> costItems;

    private BigDecimal incomeTotal;

    private BigDecimal rebateTotal;

    private BigDecimal costTotal;

    private BigDecimal gross;

    private String formulaText;

    private TripCheck tripCheck;

    @Data
    public static class Line {
        private String feeCode;
        private String name;
        private String side;
        private BigDecimal amount;
    }

    @Data
    public static class TripCheck {
        private Long tripId;
        private BigDecimal tripGross;
        private BigDecimal sumTicketGross;
        private BigDecimal diff;
        private Boolean balanced;
    }
}
