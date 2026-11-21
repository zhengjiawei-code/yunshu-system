package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量授权：N 条路由 × M 家公司，单次上限 50×50 */
@Data
public class BatchGrantRequest {

    @NotNull(message = "请选择路由域")
    private Integer routeScope;

    @NotEmpty(message = "请选择路由")
    private List<Long> routeIds;

    @NotEmpty(message = "请选择授权分公司")
    private List<Long> companyDeptIds;
}
