package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量启用/停用 */
@Data
public class TransferBatchStatusRequest {

    @NotEmpty(message = "请选择路由")
    private List<Long> ids;

    @NotNull(message = "请选择目标状态")
    private Boolean status;
}
