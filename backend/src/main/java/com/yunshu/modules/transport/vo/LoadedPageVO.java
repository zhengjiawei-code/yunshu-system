package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 已配载列表（右栏）：按子车次分组由前端聚合，接口返回含 subTripNo/destDeptName。 */
@Data
public class LoadedPageVO {

    private List<Row> records;
    private long total;
    private int page;
    private int pageSize;
    private Summary summary;

    @Data
    public static class Row {
        private Long tripWaybillId;
        private Long tripDestId;
        private String subTripNo;
        private Long destDeptId;
        private String destDeptName;
        private Long waybillId;
        private String waybillNo;
        private String parentNo;
        private Integer isChild;
        private Integer unitType;
        private BigDecimal splitRatio;
        private Integer status;
        private Integer payMethod;
        /** 结算状态：0=未结算 1=已结算 2=部分结算（按支付方式与现付金额派生） */
        private Integer settleStatus;
        private Integer loadStatus;
        private String customerName;
        private String senderName;
        private String receiverName;
        private String destName;
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
        /** 装车运费：配载时快照金额 */
        private BigDecimal loadFee;
        /** 装车返款：配载时快照返款 */
        private BigDecimal loadRebate;
        private BigDecimal validFreight;
        private BigDecimal allocatedFee;
        private BigDecimal profitCost;
        private BigDecimal grossProfit;
        private LocalDateTime loadTime;
    }

    @Data
    public static class Summary {
        private long unitCount;
        private long quantity;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal totalFee = BigDecimal.ZERO;
        private BigDecimal rebateTotal = BigDecimal.ZERO;
        private BigDecimal grossProfit = BigDecimal.ZERO;
    }
}
