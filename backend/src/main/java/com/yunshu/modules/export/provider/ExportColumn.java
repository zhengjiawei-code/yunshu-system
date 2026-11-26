package com.yunshu.modules.export.provider;

import java.util.function.Function;

/**
 * 导出列定义：列 key（与列表列 key 对齐）、表头文案、取值函数。
 */
public record ExportColumn<T>(String key, String label, Function<T, Object> extractor) {

    public Object valueOf(T record) {
        return extractor.apply(record);
    }
}
