package com.yunshu.modules.export.provider;

import java.util.List;

/**
 * 导出数据接收端：provider 只负责按批产出数据，xlsx 组装与落盘由导出服务完成。
 */
public interface RowSink {

    /** 表头（只调用一次） */
    void header(List<String> labels);

    /** 一行数据，顺序与表头一致 */
    void row(List<Object> values);
}
