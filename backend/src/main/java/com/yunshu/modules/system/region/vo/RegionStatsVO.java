package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域管理页顶部统计。
 */
@Data
@Schema(description = "区划数据统计")
public class RegionStatsVO {

    private Integer total;

    private Integer provinceCount;

    private Integer cityCount;

    private Integer districtCount;

    private String version;

    private String source;

    private LocalDateTime lastSyncTime;

    @Schema(description = "在线同步开关是否开启；关闭时页面只允许离线导入")
    private Boolean onlineSyncEnabled;
}
