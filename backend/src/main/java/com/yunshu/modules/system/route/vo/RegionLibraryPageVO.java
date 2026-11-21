package com.yunshu.modules.system.route.vo;

import java.util.List;

/**
 * 区划库分页结果：与右侧已配置路由列表同口径（records + total）。
 */
public class RegionLibraryPageVO {

    private List<RegionLibraryVO> records;
    private long total;

    public List<RegionLibraryVO> getRecords() {
        return records;
    }

    public void setRecords(List<RegionLibraryVO> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
