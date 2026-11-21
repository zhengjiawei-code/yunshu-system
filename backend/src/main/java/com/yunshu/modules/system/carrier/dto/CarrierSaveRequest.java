package com.yunshu.modules.system.carrier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 承运商新增/编辑：编码、名称、联系人、电话必填；归属机构只读由服务端写入 */
@Data
public class CarrierSaveRequest {

    @NotBlank(message = "请输入承运商编码")
    @Size(max = 32, message = "承运商编码不能超过 32 个字符")
    private String carrierCode;

    @NotBlank(message = "请输入承运商名称")
    @Size(max = 64, message = "承运商名称不能超过 64 个字符")
    private String carrierName;

    @NotBlank(message = "请输入联系人")
    @Size(max = 32, message = "联系人不能超过 32 个字符")
    private String contact;

    @NotBlank(message = "请输入联系电话")
    @Size(max = 20, message = "联系电话不能超过 20 个字符")
    private String phone;

    private Boolean status;

    private Integer sortOrder;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
