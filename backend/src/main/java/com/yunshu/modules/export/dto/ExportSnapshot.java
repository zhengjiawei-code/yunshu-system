package com.yunshu.modules.export.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出查询快照（写入 biz_export_task.query_snapshot 的 JSON 结构）。
 * 提交任务时固化数据权限与筛选条件，后台线程按快照恢复上下文取数，不依赖请求线程。
 */
@Data
public class ExportSnapshot {

    private Long userId;

    private String username;

    private Long deptId;

    /** 数据权限档位：1 全部 / 3 本机构 / 4 本机构及以下 / 5 仅本人 */
    private Integer scope;

    /** 档位 4 时的机构子树；其余档位为空 */
    private List<Long> subtreeDeptIds = new ArrayList<>();

    /** 列筛选条件：key 为列 key（兼容前端 f_ 前缀，取数时统一去前缀） */
    private Map<String, String> filters = new LinkedHashMap<>();

    private String sortBy;

    private String sortOrder;

    /** 导出列 key 顺序；为空表示使用 provider 默认列 */
    private List<String> columns = new ArrayList<>();

    /** 单次取数页大小建议值（0/空按 provider 默认） */
    private Integer pageSize;
}
