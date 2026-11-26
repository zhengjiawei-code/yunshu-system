package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增费用类型（#28）：编码由服务端生成，创建后编码/名称/类型不可改。 */
@Data
public class FeeTypeSaveRequest {

    @NotNull(message = "费用类型不能为空")
    @Min(value = 1, message = "费用类型不合法")
    @Max(value = 4, message = "费用类型不合法")
    private Integer feeCategory;

    @NotBlank(message = "费用名称不能为空")
    @Size(max = 64, message = "费用名称不能超过64个字符")
    private String feeName;

    private Boolean enabled;

    private Boolean profitEnabled;

    private Integer sortOrder;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
