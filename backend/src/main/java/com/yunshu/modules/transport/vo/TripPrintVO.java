package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 打印运输清单数据（#18）：表头 + 单元清单 + 合计；不接模板。 */
@Data
public class TripPrintVO {

    private Header header;

    private List<Row> rows;

    private Totals totals;

    @Data
    public static class Header {
        private Long tripId;
        private String tripNo;
        private String tripName;
        private Integer tripStatus;
        private String originDeptName;
        private String destNames;
        private String plateNo;
        private String vehicleType;
        private BigDecimal vehicleLoad;
        private BigDecimal vehicleVolume;
        private String driverName;
        private String driverPhone;
        private String planDepartTime;
        private String departTime;
        private String planArriveTime;
        private String allocMethodName;
    }

    @Data
    public static class Row {
        private Long tripWaybillId;
        private String waybillNo;
        private String parentNo;
        private Integer isChild;
        private String subTripNo;
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
    }

    @Data
    public static class Totals {
        private Integer unitCount;
        private Integer quantity;
        private BigDecimal weight;
        private BigDecimal volume;
        private BigDecimal totalFee;
        private BigDecimal rebateTotal;
        private BigDecimal validFreight;
    }
}
