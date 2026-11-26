package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 发车（#13）：预计到达时间必填，发车后配载/车辆/费用锁定。 */
@Data
public class DepartRequest {

    @NotNull(message = "预计到达时间不能为空")
    private LocalDateTime planArriveTime;
}
