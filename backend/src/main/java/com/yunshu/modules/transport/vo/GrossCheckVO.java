package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 车次毛利对账（#37）：车次毛利、Σ票毛利、收入−返款−成本三值，diff≠0 即分摊异常。
 */
@Data
public class GrossCheckVO {

    private Long tripId;

    /** biz_trip.gross_profit */
    private BigDecimal tripGross;

    /** Σ biz_trip_waybill.gross_profit */
    private BigDecimal sumTicketGross;

    private BigDecimal incomeTotal;

    private BigDecimal rebateTotal;

    private BigDecimal costTotal;

    /** 收入 − 返款 − 成本 */
    private BigDecimal calcGross;

    /** 车次毛利 − (收入−返款−成本) */
    private BigDecimal diff;

    private Boolean balanced;
}
