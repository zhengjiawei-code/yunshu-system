package com.yunshu.modules.export.service;

import com.yunshu.modules.export.provider.RowSink;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.util.List;

/**
 * xlsx 行接收端：SXSSF 流式写出，只持有当前行索引，不在内存保留整表。
 */
final class XlsxRowSink implements RowSink {

    private final Sheet sheet;

    private int rowIndex;

    XlsxRowSink(Sheet sheet) {
        this.sheet = sheet;
    }

    @Override
    public void header(List<String> labels) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < labels.size(); i++) {
            row.createCell(i).setCellValue(labels.get(i) == null ? "" : labels.get(i));
        }
    }

    @Override
    public void row(List<Object> values) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.size(); i++) {
            writeCell(row.createCell(i), values.get(i));
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}
