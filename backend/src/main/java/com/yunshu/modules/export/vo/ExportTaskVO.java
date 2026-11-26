package com.yunshu.modules.export.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 导出任务状态与进度（轮询接口返回）。 */
@Data
public class ExportTaskVO {

    private Long id;

    private String taskType;

    private Integer status;

    /** 状态文案：排队中 / 生成中 / 已完成 / 失败 / 已过期 */
    private String statusName;

    private Integer processedRows;

    private Integer totalRows;

    private String fileName;

    private Long fileSize;

    private LocalDateTime expireTime;

    private String failReason;

    private LocalDateTime createTime;
}
