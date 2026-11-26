package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 费用类型启用/停用（#30）。 */
@Data
public class FeeStatusRequest {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
