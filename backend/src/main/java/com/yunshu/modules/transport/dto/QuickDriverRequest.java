package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 司机快速新增（D1）。 */
@Data
public class QuickDriverRequest {

    @NotBlank(message = "司机姓名不能为空")
    @Size(max = 50, message = "司机姓名不能超过50个字符")
    private String driverName;

    @NotBlank(message = "司机电话不能为空")
    @Size(max = 20, message = "司机电话不能超过20个字符")
    private String driverPhone;

    @Size(max = 30, message = "驾驶证号不能超过30个字符")
    private String licenseNo;
}
