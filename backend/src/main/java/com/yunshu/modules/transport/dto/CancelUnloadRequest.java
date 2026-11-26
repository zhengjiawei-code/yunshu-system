package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 取消卸车（#16a）：运单级，逐票回退。 */
@Data
public class CancelUnloadRequest {

    @NotEmpty(message = "请选择要取消卸车的运单")
    private List<Long> waybillIds;

    @NotBlank(message = "操作原因不能为空")
    private String reason;
}
