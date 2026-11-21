package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步日志列表行（不返回大字段 report_json）。
 */
@Data
@Schema(description = "区划同步日志")
public class RegionSyncLogVO {

    private Long id;

    private String jobId;

    private String triggerType;

    private Long triggeredBy;

    private String triggeredByName;

    private String fileName;

    private String dataVersion;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String status;

    private Integer progress;

    private Integer totalCount;

    private Integer addedCount;

    private Integer changedCount;

    private Integer unchangedCount;

    private Integer disabledCount;

    private String errorMsg;
}
