package com.yunshu.modules.export.provider;

import com.yunshu.modules.export.dto.ExportSnapshot;

import java.util.function.IntConsumer;

/**
 * 导出数据提供者：一种 taskType 一个实现。
 * count 与 write 必须同口径（同一快照、同一筛选与数据权限），否则行数校验与文件内容会不一致。
 */
public interface ExportProvider {

    /** 任务类型，如 waybill / trip */
    String taskType();

    /** 文件名中的中文业务名，如「运单列表」 */
    String fileBaseName();

    /** 按快照统计总行数（与 write 同口径） */
    long count(ExportSnapshot snapshot);

    /**
     * 分批写出数据；每批完成后回调已处理行数，供任务进度落库。
     */
    void write(ExportSnapshot snapshot, RowSink sink, IntConsumer progress);
}
