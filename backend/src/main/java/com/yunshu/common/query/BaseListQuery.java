package com.yunshu.common.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 列表查询公共参数。
 *
 * <p>第一段只承接文本列的筛选模式，不改动各页面现有查询条件；后续页面接入时
 * 由服务层用 {@code TextFilterMode.parse(filterModes)} 解析。</p>
 */
@Data
public class BaseListQuery {

    @Schema(description = "文本列筛选模式 JSON，如 {\"username\":\"exact\"}；未配置的列按模糊匹配")
    private String filterModes;
}
