package com.yunshu.modules.operation.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 作废结果：新状态与并发令牌（updateTime） */
@Data
public class WaybillVoidVO {

    private Long id;

    private Integer status;

    private LocalDateTime updateTime;
}
