package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 到站费用保存（#36）：到站卸车费 + 到站其他费一起提交，两项可同时为 0。 */
@Data
public class ArrivalFeeRequest {

    @NotNull(message = "到站卸车费不能为空")
    @DecimalMin(value = "0", message = "到站卸车费不能为负数")
    private BigDecimal unloadFee;

    @NotNull(message = "到站其他费不能为空")
    @DecimalMin(value = "0", message = "到站其他费不能为负数")
    private BigDecimal otherFee;
}
