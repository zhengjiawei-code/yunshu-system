package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 待配载池：只返回已通过全部资格校验的可配单元。 */
@Data
public class PoolPageVO {

    private List<Row> records;
    private long total;
    private int page;
    private int pageSize;
    private Summary summary;

    @Data
    public static class Row {
        private Long waybillId;
        private String waybillNo;
        /** 1=整票 2=已拆母单余额 3=子单 */
        private Integer unitType;
        private String parentNo;
        private Integer childCount;
        private BigDecimal splitRatio;
        private Integer status;
        private String customerName;
        private String receiverName;
        private String receiverPhone;
        private String goodsName;
        private String destName;
        private Long nextDeptId;
        private String nextDeptName;
        private Integer originalQuantity;
        private BigDecimal originalWeight;
        private BigDecimal originalVolume;
        private Integer remainingQuantity;
        private BigDecimal remainingWeight;
        private BigDecimal remainingVolume;
        private BigDecimal totalFee;
        /** 运费余额：母单余额按剩余金额口径；整票/子单即本单元运费合计 */
        private BigDecimal remainFee;
        private BigDecimal rebateTotal;
        private BigDecimal validFreight;
        private LocalDateTime inventoryTime;
        private LocalDateTime createTime;
    }

    @Data
    public static class Summary {
        private long unitCount;
        private long quantity;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
    }
}
