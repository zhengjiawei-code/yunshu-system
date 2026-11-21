package com.yunshu.modules.system.columnconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列配置回读结果：config 为空表示未自定义，前端使用页面默认配置。
 */
@Data
@Schema(description = "列表列配置")
public class ColumnConfigVO {

    private String pageKey;

    @Schema(description = "列配置JSON；为空表示未自定义")
    private String config;

    @Schema(description = "是否已自定义（存在用户配置行）")
    private Boolean customized;

    private LocalDateTime updateTime;
}
