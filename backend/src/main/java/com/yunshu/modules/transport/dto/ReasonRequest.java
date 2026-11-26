package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 取消发车/关闭车次等需要原因的操作（#14/#17）。 */
@Data
public class ReasonRequest {

    @NotBlank(message = "操作原因不能为空")
    @Size(max = 500, message = "操作原因不能超过500个字符")
    private String reason;
}
