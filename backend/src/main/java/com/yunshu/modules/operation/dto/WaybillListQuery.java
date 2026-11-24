package com.yunshu.modules.operation.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运单列表查询参数（GET）。
 * 约定：
 * 1) 分页与排序：page / pageSize / sortBy / sortOrder；
 * 2) 文本列筛选模式：filterModes（JSON，如 {"waybillNo":"like"}，与前端 utils/textFilter.ts 同口径）；
 * 3) 列筛选值统一用 f_&lt;字段名&gt; 前缀，例如 f_waybillNo、f_status；服务层按白名单解析，未知键直接忽略。
 *    多选值逗号分隔；数值/日期区间用 ~ 分隔（f_quantity=1~100、f_createTime=起始~结束）。
 */
@Data
public class WaybillListQuery {

    private int page = 1;

    private int pageSize = 20;

    private String sortBy;

    private String sortOrder;

    private String filterModes;

    private final Map<String, String> filters = new LinkedHashMap<>();

    public static WaybillListQuery from(Map<String, String> params) {
        WaybillListQuery query = new WaybillListQuery();
        if (params == null || params.isEmpty()) {
            return query;
        }
        params.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            switch (key) {
                case "page" -> query.setPage(parseInt(value, 1));
                case "pageSize" -> query.setPageSize(parseInt(value, 20));
                case "sortBy" -> query.setSortBy(value.trim());
                case "sortOrder" -> query.setSortOrder(value.trim());
                case "filterModes" -> query.setFilterModes(value);
                default -> {
                    if (key.startsWith("f_") && key.length() > 2) {
                        query.filters.put(key.substring(2), value);
                    }
                }
            }
        });
        return query;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}
