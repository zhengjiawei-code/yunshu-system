package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 车次操作日志（#19）。 */
@Data
public class TripLogVO {

    private Long id;

    private Integer actionType;

    private String actionName;

    private Long destId;

    private String reason;

    private String operatorName;

    private String beforeSnapshot;

    private String afterSnapshot;

    private LocalDateTime createTime;
}
