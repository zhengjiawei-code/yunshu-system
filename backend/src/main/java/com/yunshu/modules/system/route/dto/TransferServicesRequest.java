package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 外转/直达路由提供服务：仅更新自提、送货两个字段 */
@Data
public class TransferServicesRequest {

    @NotNull(message = "请选择是否提供自提")
    private Boolean pickupEnabled;

    @NotNull(message = "请选择是否提供送货")
    private Boolean deliveryEnabled;
}
