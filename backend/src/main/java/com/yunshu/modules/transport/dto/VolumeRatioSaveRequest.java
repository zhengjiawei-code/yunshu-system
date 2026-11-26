package com.yunshu.modules.transport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 保存全局重泡比与线路覆盖（#32）：线路值可为空，非空必须 >0。 */
@Data
public class VolumeRatioSaveRequest {

    @NotNull(message = "全局重泡比不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "全局重泡比必须大于0")
    private BigDecimal globalRatio;

    @Valid
    private List<RouteItem> routes;

    @Data
    public static class RouteItem {
        @NotNull(message = "线路ID不能为空")
        private Long routeId;
        @DecimalMin(value = "0", inclusive = false, message = "线路重泡比必须大于0或留空")
        private BigDecimal volumeWeightRatio;
    }
}
