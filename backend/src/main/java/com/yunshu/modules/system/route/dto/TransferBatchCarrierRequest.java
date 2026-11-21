package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量挂载承运商（仅外转生效，直达行跳过） */
@Data
public class TransferBatchCarrierRequest {

    @NotEmpty(message = "请选择路由")
    private List<Long> ids;

    @NotNull(message = "请选择承运商")
    private Long carrierId;
}
