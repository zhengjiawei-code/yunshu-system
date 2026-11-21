package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 单个路由授权/取消授权 */
@Data
public class GrantRequest {

    @NotEmpty(message = "请选择授权分公司")
    private List<Long> companyDeptIds;
}
