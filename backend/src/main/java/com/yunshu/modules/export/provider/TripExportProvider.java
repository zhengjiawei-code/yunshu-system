package com.yunshu.modules.export.provider;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.export.dto.ExportSnapshot;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.TripListQuery;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.service.TripService;
import com.yunshu.modules.transport.vo.TripPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 车次列表导出（taskType=trip）。
 * 复用 TripService#page，筛选/排序/数据权限与列表一致；目的网点按 biz_trip_dest 名称拼接。
 */
@Component
@RequiredArgsConstructor
public class TripExportProvider implements ExportProvider {

    public static final String TASK_TYPE = "trip";

    private static final int DEFAULT_PAGE_SIZE = 1000;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<ExportColumn<BizTrip>> COLUMNS = List.of(
            new ExportColumn<>("tripNo", "车次号", BizTrip::getTripNo),
            new ExportColumn<>("tripName", "车次名称", BizTrip::getTripName),
            new ExportColumn<>("tripStatus", "车次状态", t -> TripEnums.TripStatus.name(t.getTripStatus())),
            new ExportColumn<>("originDeptName", "始发网点", BizTrip::getOriginDeptName),
            new ExportColumn<>("destNames", "目的网点", t -> null),
            new ExportColumn<>("plateNo", "车牌号", BizTrip::getPlateNo),
            new ExportColumn<>("driverName", "司机", BizTrip::getDriverName),
            new ExportColumn<>("planDepartTime", "计划发车时间", t -> formatDateTime(t.getPlanDepartTime())),
            new ExportColumn<>("departTime", "实际发车时间", t -> formatDateTime(t.getDepartTime())),
            new ExportColumn<>("totalWaybillCount", "配载票数", BizTrip::getTotalWaybillCount),
            new ExportColumn<>("totalQuantity", "总件数", BizTrip::getTotalQuantity),
            new ExportColumn<>("totalWeight", "总重量", BizTrip::getTotalWeight),
            new ExportColumn<>("totalVolume", "总容积", BizTrip::getTotalVolume),
            new ExportColumn<>("totalFreight", "收入合计", BizTrip::getTotalFreight),
            new ExportColumn<>("totalRebate", "返款合计", BizTrip::getTotalRebate),
            new ExportColumn<>("totalCostFee", "成本合计", BizTrip::getTotalCostFee),
            new ExportColumn<>("grossProfit", "毛利", BizTrip::getGrossProfit),
            new ExportColumn<>("createTime", "创建时间", t -> formatDateTime(t.getCreateTime()))
    );

    private final TripService tripService;

    private final BizTripDestMapper destMapper;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public String fileBaseName() {
        return "车次列表";
    }

    @Override
    public long count(ExportSnapshot snapshot) {
        TripPageVO result = tripService.page(toQuery(snapshot, 1, 1));
        return result.getTotal();
    }

    @Override
    public void write(ExportSnapshot snapshot, RowSink sink, IntConsumer progress) {
        List<ExportColumn<BizTrip>> columns = selectColumns(snapshot.getColumns());
        sink.header(columns.stream().map(ExportColumn::label).toList());

        int pageSize = normalizePageSize(snapshot.getPageSize());
        int page = 1;
        int processed = 0;
        while (true) {
            TripPageVO result = tripService.page(toQuery(snapshot, page, pageSize));
            List<BizTrip> records = result.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            Map<Long, String> destNames = loadDestNames(records);
            for (BizTrip record : records) {
                List<Object> values = new ArrayList<>(columns.size());
                for (ExportColumn<BizTrip> column : columns) {
                    values.add("destNames".equals(column.key())
                            ? destNames.get(record.getId())
                            : column.valueOf(record));
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

    /** 目的网点名称拼接，保持 seq 顺序 */
    private Map<Long, String> loadDestNames(List<BizTrip> trips) {
        List<Long> tripIds = trips.stream().map(BizTrip::getId).filter(java.util.Objects::nonNull).toList();
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .select(BizTripDest::getTripId, BizTripDest::getDestDeptName)
                .in(BizTripDest::getTripId, tripIds)
                .orderByAsc(BizTripDest::getSeq)
                .orderByAsc(BizTripDest::getId));
        Map<Long, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (BizTripDest dest : dests) {
            if (dest.getTripId() == null || dest.getDestDeptName() == null) {
                continue;
            }
            grouped.computeIfAbsent(dest.getTripId(), key -> new LinkedHashSet<>())
                    .add(dest.getDestDeptName());
        }
        Map<Long, String> result = new HashMap<>();
        grouped.forEach((tripId, names) -> result.put(tripId, String.join("、", names)));
        return result;
    }

    /** 按请求列顺序裁剪；请求为空或全部未知时回退默认列 */
    private List<ExportColumn<BizTrip>> selectColumns(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return COLUMNS;
        }
        Map<String, ExportColumn<BizTrip>> index = new LinkedHashMap<>();
        for (ExportColumn<BizTrip> column : COLUMNS) {
            index.put(column.key(), column);
        }
        List<ExportColumn<BizTrip>> selected = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        for (String key : requested) {
            if (key == null || !visited.add(key)) {
                continue;
            }
            ExportColumn<BizTrip> column = index.get(key.trim());
            if (column != null) {
                selected.add(column);
            }
        }
        return selected.isEmpty() ? COLUMNS : selected;
    }

    private TripListQuery toQuery(ExportSnapshot snapshot, int page, int pageSize) {
        TripListQuery query = new TripListQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        if (snapshot == null) {
            return query;
        }
        query.setSortBy(snapshot.getSortBy());
        query.setSortOrder(snapshot.getSortOrder());
        if (snapshot.getFilters() == null) {
            return query;
        }
        snapshot.getFilters().forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalized = key.startsWith("f_") ? key.substring(2) : key;
            switch (normalized) {
                case "tripNo" -> query.setTripNo(value);
                case "tripName" -> query.setTripName(value);
                case "plateNo" -> query.setPlateNo(value);
                case "driverName" -> query.setDriverName(value);
                case "status", "tripStatus" -> query.setStatus(parseInts(value));
                case "originDeptId", "originDeptName" -> query.setOriginDeptId(parseLong(value));
                case "destDeptId", "destDeptName" -> query.setDestDeptId(parseLong(value));
                case "planDepartStart" -> query.setPlanDepartStart(value);
                case "planDepartEnd" -> query.setPlanDepartEnd(value);
                case "departStart" -> query.setDepartStart(value);
                case "departEnd" -> query.setDepartEnd(value);
                case "createStart" -> query.setCreateStart(value);
                case "createEnd" -> query.setCreateEnd(value);
                default -> {
                    // 未知筛选键忽略，与列表口径一致
                }
            }
        });
        return query;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(pageSize, 100), DEFAULT_PAGE_SIZE);
    }

    private static List<Integer> parseInts(String value) {
        List<Integer> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split("[,，]")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(item));
            } catch (NumberFormatException ignored) {
                // 非法码值忽略
            }
        }
        return result;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }
}
