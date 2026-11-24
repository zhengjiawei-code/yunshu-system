package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 外转/直达路由别名：仅更新 alias */
@Data
public class TransferAliasRequest {

    @NotBlank(message = "别名不能为空")
    @Size(max = 64, message = "别名不能超过 64 个字符")
    private String alias;
}
