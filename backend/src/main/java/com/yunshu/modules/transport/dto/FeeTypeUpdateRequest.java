package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 编辑费用类型（#29）：名称/编码/类型不可改，仅启用、参与毛利、排序、备注。 */
@Data
public class FeeTypeUpdateRequest {

    private Boolean enabled;

    private Boolean profitEnabled;

    private Integer sortOrder;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
