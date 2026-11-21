package com.yunshu.modules.system.region.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区划导入/同步任务日志，对应 sys_region_sync_log。
 */
@Data
@TableName("sys_region_sync_log")
public class SysRegionSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobId;

    /** offline / online */
    private String triggerType;

    private Long triggeredBy;

    private String fileName;

    private String dataVersion;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** pending / running / success / failed / partial */
    private String status;

    private Integer progress;

    private Integer totalCount;

    private Integer addedCount;

    private Integer changedCount;

    private Integer unchangedCount;

    private Integer disabledCount;

    private String reportJson;

    private String errorMsg;

    private LocalDateTime createTime;
}
