package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 车辆快速新增（D1）。 */
@Data
public class QuickVehicleRequest {

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 20, message = "车牌号不能超过20个字符")
    private String plateNo;

    @Size(max = 50, message = "车辆型号不能超过50个字符")
    private String vehicleType;

    private BigDecimal vehicleLoad;

    private BigDecimal vehicleVolume;
}
