package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.util.List;

/** 卸车（#35）：waybillIds 为空表示按 0 票完成子车次（大车直送不参与）。 */
@Data
public class UnloadRequest {

    private List<Long> waybillIds;
}
