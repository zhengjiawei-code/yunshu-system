package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 子车次确认到站（#34），arriveTime 为空时取当前时间。 */
@Data
public class ArriveRequest {

    private LocalDateTime arriveTime;
}
