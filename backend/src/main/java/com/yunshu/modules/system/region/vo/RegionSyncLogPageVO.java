package com.yunshu.modules.system.region.vo;

import lombok.Data;

import java.util.List;

/**
 * 同步日志分页结果。
 */
@Data
public class RegionSyncLogPageVO {

    private List<RegionSyncLogVO> records;

    private Long total;
}
