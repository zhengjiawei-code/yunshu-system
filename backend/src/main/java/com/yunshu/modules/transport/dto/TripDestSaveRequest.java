package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 新增/修改目的网点请求。 */
@Data
public class TripDestSaveRequest {

    private Long destDeptId;

    private Integer seq;

    private LocalDateTime planArriveTime;
}
