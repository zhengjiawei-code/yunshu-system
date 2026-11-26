package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 拆单装车：只录本车件数。 */
@Data
public class SplitRequest {

    @NotNull(message = "母单ID不能为空")
    private Long parentWaybillId;

    @NotNull(message = "本车装载件数不能为空")
    @Min(value = 1, message = "本车装载件数至少1件")
    private Integer quantity;
}
