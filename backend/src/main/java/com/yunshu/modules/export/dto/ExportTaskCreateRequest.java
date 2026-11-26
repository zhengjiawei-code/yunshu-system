package com.yunshu.modules.export.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发起导出任务入参（POST /api/v1/export/tasks）。
 * filters 为列筛选值（与列表同一口径，兼容 f_ 前缀）；sort 为排序键；columns 为当前可见列 key，按顺序导出。
 */
@Data
public class ExportTaskCreateRequest {

    private String taskType;

    private Map<String, String> filters = new LinkedHashMap<>();

    private Sort sort;

    private List<String> columns = new ArrayList<>();

    private Integer pageSize;

    /** 列表排序 */
    @Data
    public static class Sort {

        private String sortBy;

        private String sortOrder;
    }
}
