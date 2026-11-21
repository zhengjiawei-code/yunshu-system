package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量切换直达/外转（切直达自动清空承运商并重写编码后缀） */
@Data
public class TransferBatchTypeRequest {

    @NotEmpty(message = "请选择路由")
    private List<Long> ids;

    @NotNull(message = "请选择目标类型")
    private Integer transferType;
}
