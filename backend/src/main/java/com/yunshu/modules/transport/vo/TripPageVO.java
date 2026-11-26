package com.yunshu.modules.transport.vo;

import com.yunshu.modules.transport.entity.BizTrip;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 车次分页结果 + 全筛选条件合计。 */
@Data
public class TripPageVO {

    private List<BizTrip> records;

    private long total;

    private int page;

    private int pageSize;

    private Summary summary;

    @Data
    public static class Summary {
        private long tripCount;
        private long waybillCount;
        private long quantity;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal totalFreight = BigDecimal.ZERO;
        private BigDecimal grossProfit = BigDecimal.ZERO;
    }
}
