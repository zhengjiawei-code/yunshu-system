package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入执行结果：在预检统计之上补充任务信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "区划导入结果")
public class RegionImportResultVO extends RegionImportCheckVO {

    private Long logId;

    private String jobId;

    @Schema(description = "running / success / failed / partial")
    private String status;

    private String message;
}
