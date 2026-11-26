package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 全局重泡比 + 线路级覆盖（#31/#32）。 */
@Data
public class VolumeRatioVO {

    private BigDecimal globalRatio;

    private List<RouteRow> routes;

    @Data
    public static class RouteRow {
        private Long routeId;
        private String routeCode;
        private String routeName;
        /** 线路配置值，null/0 表示未覆盖 */
        private BigDecimal volumeWeightRatio;
        /** 实际生效值：线路 >0 用线路，否则用全局 */
        private BigDecimal effectiveRatio;
    }
}
