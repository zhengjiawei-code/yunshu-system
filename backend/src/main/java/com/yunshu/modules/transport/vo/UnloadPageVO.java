package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 到站卸车作业页初始化（#33）：信息条 + Tab 计数 + 运单列表 + 内联费用区数据。
 * 全部口径排除 delivery_method=3 大车直送。
 */
@Data
public class UnloadPageVO {

    private TripInfo trip;

    private DestInfo dest;

    private Counts counts;

    private Summary summary;

    private List<Record> records;

    private Integer total;

    private Integer page;

    private Integer pageSize;

    private Boolean feeEditable;

    private Boolean unloadFeeValid;

    private Boolean otherFeeValid;

    private BigDecimal arrivalUnloadFee;

    private BigDecimal arrivalOtherFee;

    private BigDecimal arrivalTransportFee;

    private Integer allocMethod;

    private String allocMethodName;

    private Boolean canCancelArrive;

    private Boolean canCancelUnload;

    /** 未发车/已关闭 → 只读 */
    private Boolean readonly;

    @Data
    public static class TripInfo {
        private Long tripId;
        private String tripNo;
        private String tripName;
        private Integer tripStatus;
        private String tripStatusName;
        private Integer tripCategory;
        private String plateNo;
        private String driverName;
        private String originDeptName;
    }

    @Data
    public static class DestInfo {
        private Long destId;
        private String subTripNo;
        private Long destDeptId;
        private String destDeptName;
        private Integer subStatus;
        private LocalDateTime arriveTime;
        private LocalDateTime unloadTime;
    }

    @Data
    public static class Counts {
        private Integer all;
        private Integer pending;
        private Integer done;
    }

    @Data
    public static class Summary {
        private Integer unitCount;
        private Integer quantity;
        private BigDecimal weight;
        private BigDecimal volume;
    }

    @Data
    public static class Record {
        private Long tripWaybillId;
        private Long waybillId;
        private String waybillNo;
        private String parentNo;
        private Integer isChild;
        private Integer status;
        private String statusName;
        private Integer deliveryMethod;
        private String deliveryMethodName;
        private String destName;
        private String goodsName;
        private Integer quantity;
        private BigDecimal weight;
        private BigDecimal volume;
        private BigDecimal freightFee;
        private BigDecimal deliveryFee;
        private BigDecimal collectFee;
        private String receiverName;
        private String receiverPhone;
        private LocalDateTime createTime;
        private Integer loadStatus;
        private Boolean unloaded;
    }
}
