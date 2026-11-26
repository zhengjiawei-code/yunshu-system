package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 配载/移出请求：批量配载单元 ID（整票=母单ID，拆单=子单ID）。 */
@Data
public class LoadRequest {

    @NotEmpty(message = "请选择要操作的运单")
    private List<Long> waybillIds;
}
