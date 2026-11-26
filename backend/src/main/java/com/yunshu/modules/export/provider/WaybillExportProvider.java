package com.yunshu.modules.export.provider;

import com.yunshu.modules.export.dto.ExportSnapshot;
import com.yunshu.modules.operation.constant.WaybillEnums;
import com.yunshu.modules.operation.dto.WaybillListQuery;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.service.WaybillQueryService;
import com.yunshu.modules.operation.vo.WaybillPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 运单列表导出（taskType=waybill）。
 * 复用列表查询 WaybillQueryService#page，固定母单口径；筛选/排序/数据权限与列表完全一致。
 * 列 key 与前端运单列表列 key 对齐，请求 columns 非空时按传入顺序裁剪，未知列忽略。
 */
@Component
@RequiredArgsConstructor
public class WaybillExportProvider implements ExportProvider {

    public static final String TASK_TYPE = "waybill";

    /** 建议取数页大小；列表服务内部有上限，实际以返回 pageSize 为准 */
    private static final int DEFAULT_PAGE_SIZE = 1000;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认导出列（方案 9.1：运单列表常用字段） */
    private static final List<ExportColumn<BizWaybill>> COLUMNS = List.of(
            new ExportColumn<>("waybillNo", "运单号", BizWaybill::getWaybillNo),
            new ExportColumn<>("status", "状态", w -> WaybillEnums.Status.labelOf(w.getStatus())),
            new ExportColumn<>("senderName", "发货人", BizWaybill::getSenderName),
            new ExportColumn<>("receiverName", "收货人", BizWaybill::getReceiverName),
            new ExportColumn<>("receiverPhone", "收货电话", BizWaybill::getReceiverPhone),
            new ExportColumn<>("destName", "目的地", BizWaybill::getDestName),
            new ExportColumn<>("goodsName", "货物名称", BizWaybill::getGoodsName),
            new ExportColumn<>("quantity", "件数", BizWaybill::getQuantity),
            new ExportColumn<>("weight", "重量", BizWaybill::getWeight),
            new ExportColumn<>("volume", "体积", BizWaybill::getVolume),
            new ExportColumn<>("totalFee", "运费合计", BizWaybill::getTotalFee),
            new ExportColumn<>("prepaidFee", "现付", BizWaybill::getPrepaidFee),
            new ExportColumn<>("collectFee", "到付", BizWaybill::getCollectFee),
            new ExportColumn<>("returnFee", "回付", BizWaybill::getReturnFee),
            new ExportColumn<>("monthlyFee", "月结", BizWaybill::getMonthlyFee),
            new ExportColumn<>("rebateTotal", "返款", BizWaybill::getRebateTotal),
            new ExportColumn<>("validFreight", "有效运费", BizWaybill::getValidFreight),
            new ExportColumn<>("deliveryMethod", "送货方式",
                    w -> WaybillEnums.DeliveryMethod.labelOf(w.getDeliveryMethod())),
            new ExportColumn<>("createTime", "创建时间", w -> formatDateTime(w.getCreateTime()))
    );

    private final WaybillQueryService waybillQueryService;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public String fileBaseName() {
        return "运单列表";
    }

    @Override
    public long count(ExportSnapshot snapshot) {
        WaybillPageVO result = waybillQueryService.page(toQuery(snapshot, 1, 1));
        return result.getTotal();
    }

    @Override
    public void write(ExportSnapshot snapshot, RowSink sink, IntConsumer progress) {
        List<ExportColumn<BizWaybill>> columns = selectColumns(snapshot.getColumns());
        sink.header(columns.stream().map(ExportColumn::label).toList());

        int pageSize = normalizePageSize(snapshot.getPageSize());
        int page = 1;
        int processed = 0;
        while (true) {
            WaybillPageVO result = waybillQueryService.page(toQuery(snapshot, page, pageSize));
            List<BizWaybill> records = result.getData();
            if (records == null || records.isEmpty()) {
                break;
            }
            for (BizWaybill record : records) {
                List<Object> values = new ArrayList<>(columns.size());
                for (ExportColumn<BizWaybill> column : columns) {
                    values.add(column.valueOf(record));
                }
                sink.row(values);
            }
            processed += records.size();
            progress.accept(processed);
            long effective = result.getPageSize();
            if (effective <= 0 || records.size() < effective) {
                break;
            }
            page++;
        }
    }

    /** 按请求列顺序裁剪；请求为空或全部未知时回退默认列 */
    private List<ExportColumn<BizWaybill>> selectColumns(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return COLUMNS;
        }
        Map<String, ExportColumn<BizWaybill>> index = new LinkedHashMap<>();
        for (ExportColumn<BizWaybill> column : COLUMNS) {
            index.put(column.key(), column);
        }
        List<ExportColumn<BizWaybill>> selected = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        for (String key : requested) {
            if (key == null || !visited.add(key)) {
                continue;
            }
            ExportColumn<BizWaybill> column = index.get(key.trim());
            if (column != null) {
                selected.add(column);
            }
        }
        return selected.isEmpty() ? COLUMNS : selected;
    }

    private WaybillListQuery toQuery(ExportSnapshot snapshot, int page, int pageSize) {
        WaybillListQuery query = new WaybillListQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        if (snapshot != null) {
            query.setSortBy(snapshot.getSortBy());
            query.setSortOrder(snapshot.getSortOrder());
            if (snapshot.getFilters() != null) {
                snapshot.getFilters().forEach((key, value) -> {
                    if (key == null || value == null) {
                        return;
                    }
                    // 前端按列表口径传 f_ 前缀，服务层 filters 用无前缀 key
                    String normalized = key.startsWith("f_") ? key.substring(2) : key;
                    query.getFilters().put(normalized, value);
                });
            }
        }
        return query;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(pageSize, 100), DEFAULT_PAGE_SIZE);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }
}
