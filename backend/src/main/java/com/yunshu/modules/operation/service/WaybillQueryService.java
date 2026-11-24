package com.yunshu.modules.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.common.filter.TextFilterMode;
import com.yunshu.modules.operation.dto.WaybillListQuery;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.operation.vo.WaybillChildrenVO;
import com.yunshu.modules.operation.vo.WaybillPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运单列表与子单清单查询（方案第 3–5、12 节）。
 * 只返回母单；筛选字段走白名单，排序字段走白名单，未知键直接忽略。
 * 数据权限（四档）与逻辑删除由 MyBatis-Plus 拦截器自动追加，服务层不重复拼 dept 条件。
 */
@Service
@RequiredArgsConstructor
public class WaybillQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String DEFAULT_SORT_COLUMN = "create_time";

    private final BizWaybillMapper waybillMapper;

    /** 列筛选白名单：前端列 key -> 库列 + 类型 */
    private static final Map<String, FilterField> FILTER_FIELDS = buildFilterFields();

    /** 排序白名单：前端列 key -> 库列 */
    private static final Map<String, String> SORT_FIELDS = buildSortFields();

    /** 合计项：库列 -> 返回 key；声明价值不参与合计（方案 3.1） */
    private static final String[][] SUMMARY_FIELDS = {
            {"quantity", "quantity"},
            {"weight", "weight"},
            {"volume", "volume"},
            {"freight_fee", "freightFee"},
            {"delivery_fee", "deliveryFee"},
            {"pickup_fee", "pickupFee"},
            {"other_fee", "otherFee"},
            {"receipt_fee", "receiptFee"},
            {"make_bill_fee", "makeBillFee"},
            {"insurance_fee", "insuranceFee"},
            {"total_fee", "totalFee"},
            {"cash_back", "cashBack"},
            {"owe_back", "oweBack"},
            {"rebate_total", "rebateTotal"},
            {"valid_freight", "validFreight"},
            {"prepaid_fee", "prepaidFee"},
            {"collect_fee", "collectFee"},
            {"monthly_fee", "monthlyFee"},
            {"return_fee", "returnFee"},
            {"cod_amount", "codAmount"},
            {"receipt_count", "receiptCount"}
    };

    public WaybillPageVO page(WaybillListQuery query) {
        int pageNum = query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());

        QueryWrapper<BizWaybill> wrapper = new QueryWrapper<>();
        wrapper.eq("is_child", 0);
        applyFilters(wrapper, query.getFilters(), modes);
        applySort(wrapper, query.getSortBy(), query.getSortOrder());

        Page<BizWaybill> page = waybillMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizWaybill> records = page.getRecords();
        fillChildCount(records);

        WaybillPageVO vo = new WaybillPageVO();
        vo.setData(records);
        vo.setTotal(page.getTotal());
        vo.setPage(pageNum);
        vo.setPageSize(pageSize);
        vo.setSummary(summary(query));
        return vo;
    }

    public WaybillChildrenVO children(Long parentId) {
        BizWaybill parent = parentId == null ? null : waybillMapper.selectById(parentId);
        if (parent == null) {
            throw new com.yunshu.common.exception.BizException(403, "无权访问该运单");
        }
        if (parent.getIsChild() != null && parent.getIsChild() == 1) {
            // 子单没有下级；返回自身三量，便于统一渲染
            WaybillChildrenVO vo = new WaybillChildrenVO();
            vo.setParentId(parent.getParentId());
            vo.setParentNo(parent.getParentNo());
            vo.setChildren(List.of());
            vo.setParentTotals(totals(parent.getQuantity(), parent.getWeight(), parent.getVolume()));
            vo.setChildTotals(totals(0, BigDecimal.ZERO, BigDecimal.ZERO));
            return vo;
        }
        List<BizWaybill> children = waybillMapper.selectList(new QueryWrapper<BizWaybill>()
                .eq("parent_id", parent.getId())
                .orderByAsc("child_seq")
                .orderByAsc("id"));
        int quantity = 0;
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        for (BizWaybill child : children) {
            quantity += child.getQuantity() == null ? 0 : child.getQuantity();
            weight = weight.add(child.getWeight() == null ? BigDecimal.ZERO : child.getWeight());
            volume = volume.add(child.getVolume() == null ? BigDecimal.ZERO : child.getVolume());
        }
        WaybillChildrenVO vo = new WaybillChildrenVO();
        vo.setParentId(parent.getId());
        vo.setParentNo(parent.getWaybillNo());
        vo.setChildren(children);
        vo.setParentTotals(totals(parent.getQuantity(), parent.getWeight(), parent.getVolume()));
        vo.setChildTotals(totals(quantity, weight, volume));
        return vo;
    }

    private void applyFilters(QueryWrapper<BizWaybill> wrapper, Map<String, String> filters,
                              Map<String, TextFilterMode> modes) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        filters.forEach((key, value) -> {
            FilterField field = FILTER_FIELDS.get(key);
            if (field == null || !StringUtils.hasText(value)) {
                return;
            }
            String raw = value.trim();
            switch (field.type()) {
                case TEXT -> applyText(wrapper, field.column(), raw, TextFilterMode.modeOf(modes, key));
                case ENUM_MULTI -> applyMulti(wrapper, field.column(), raw);
                case NUMBER_RANGE -> applyNumberRange(wrapper, field.column(), raw);
                case DATETIME_RANGE -> applyDateRange(wrapper, field.column(), raw);
                case ID -> applyId(wrapper, field.column(), raw);
                default -> {
                }
            }
        });
    }

    private void applyText(QueryWrapper<BizWaybill> wrapper, String column, String raw, TextFilterMode mode) {
        TextFilterMode effective = mode == null ? TextFilterMode.LIKE : mode;
        switch (effective) {
            case EXACT -> wrapper.eq(column, raw);
            case NE -> wrapper.ne(column, raw);
            case LEFT -> wrapper.likeRight(column, raw);
            case RIGHT -> wrapper.likeLeft(column, raw);
            case BATCH -> {
                List<String> values = TextFilterMode.batchValues(raw);
                if (!values.isEmpty()) {
                    wrapper.in(column, values);
                }
            }
            case LIKE -> wrapper.like(column, raw);
            default -> wrapper.like(column, raw);
        }
    }

    private void applyMulti(QueryWrapper<BizWaybill> wrapper, String column, String raw) {
        List<Integer> values = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(item));
            } catch (NumberFormatException ignored) {
                // 非法码值忽略，避免整页报错
            }
        }
        if (!values.isEmpty()) {
            wrapper.in(column, values);
        }
    }

    private void applyNumberRange(QueryWrapper<BizWaybill> wrapper, String column, String raw) {
        if (!raw.contains("~")) {
            wrapper.eq(column, new BigDecimal(raw));
            return;
        }
        String[] parts = raw.split("~", -1);
        String min = parts.length > 0 ? parts[0].trim() : "";
        String max = parts.length > 1 ? parts[1].trim() : "";
        if (!min.isEmpty()) {
            wrapper.ge(column, new BigDecimal(min));
        }
        if (!max.isEmpty()) {
            wrapper.le(column, new BigDecimal(max));
        }
    }

    private void applyDateRange(QueryWrapper<BizWaybill> wrapper, String column, String raw) {
        String[] parts = raw.split("~", -1);
        if (parts.length != 2) {
            return;
        }
        LocalDateTime start = parseStart(parts[0].trim());
        LocalDateTime end = parseEnd(parts[1].trim());
        if (start != null) {
            wrapper.ge(column, start);
        }
        if (end != null) {
            wrapper.le(column, end);
        }
    }

    private void applyId(QueryWrapper<BizWaybill> wrapper, String column, String raw) {
        try {
            wrapper.eq(column, Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            // 非法 id 忽略
        }
    }

    private void applySort(QueryWrapper<BizWaybill> wrapper, String sortBy, String sortOrder) {
        String column = StringUtils.hasText(sortBy) ? SORT_FIELDS.get(sortBy.trim()) : null;
        boolean asc = "asc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
        if (column == null) {
            wrapper.orderByDesc(DEFAULT_SORT_COLUMN);
            wrapper.orderByDesc("waybill_no");
        } else {
            wrapper.orderBy(true, asc, column);
            if (DEFAULT_SORT_COLUMN.equals(column)) {
                wrapper.orderByDesc("waybill_no");
            }
        }
        // 稳定分页兜底，禁止不确定顺序
        wrapper.orderByDesc("id");
    }

    private void fillChildCount(List<BizWaybill> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> parentIds = records.stream()
                .filter(item -> item.getIsChild() != null && item.getIsChild() == 0)
                .map(BizWaybill::getId)
                .toList();
        if (parentIds.isEmpty()) {
            return;
        }
        QueryWrapper<BizWaybill> wrapper = new QueryWrapper<>();
        wrapper.select("parent_id AS parentId", "COUNT(*) AS cnt")
                .in("parent_id", parentIds)
                .groupBy("parent_id");
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : waybillMapper.selectMaps(wrapper)) {
            Object parentId = row.get("parentId");
            Object cnt = row.get("cnt");
            if (parentId instanceof Number id && cnt instanceof Number number) {
                counts.put(id.longValue(), number.intValue());
            }
        }
        for (BizWaybill item : records) {
            int count = counts.getOrDefault(item.getId(), 0);
            item.setChildCount(count);
            item.setHasChildren(count > 0);
        }
    }

    private Map<String, BigDecimal> summary(WaybillListQuery query) {
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());
        QueryWrapper<BizWaybill> wrapper = new QueryWrapper<>();
        wrapper.eq("is_child", 0);
        applyFilters(wrapper, query.getFilters(), modes);
        String[] selects = new String[SUMMARY_FIELDS.length];
        for (int i = 0; i < SUMMARY_FIELDS.length; i++) {
            selects[i] = "IFNULL(SUM(" + SUMMARY_FIELDS[i][0] + "), 0) AS " + SUMMARY_FIELDS[i][1];
        }
        wrapper.select(selects);
        List<Map<String, Object>> rows = waybillMapper.selectMaps(wrapper);
        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        for (String[] field : SUMMARY_FIELDS) {
            summary.put(field[1], toDecimal(row.get(field[1])));
        }
        return summary;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Map<String, BigDecimal> totals(Integer quantity, BigDecimal weight, BigDecimal volume) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("quantity", BigDecimal.valueOf(quantity == null ? 0 : quantity));
        totals.put("weight", weight == null ? BigDecimal.ZERO : weight);
        totals.put("volume", volume == null ? BigDecimal.ZERO : volume);
        return totals;
    }

    private LocalDateTime parseStart(String value) {
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() <= 10) {
            return LocalDate.parse(value.substring(0, 10)).atStartOfDay();
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private LocalDateTime parseEnd(String value) {
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() <= 10) {
            return LocalDate.parse(value.substring(0, 10)).atTime(23, 59, 59);
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static Map<String, FilterField> buildFilterFields() {
        Map<String, FilterField> map = new LinkedHashMap<>();
        // 文本列（支持 filterModes 六种模式）
        for (String key : List.of("waybillNo", "relatedNo", "customerName", "destName", "senderName",
                "receiverName", "routeName", "createByName", "originHubName", "deptName",
                "destProvinceName", "destCityName", "destDistrictName", "salesName",
                "senderPhone", "senderAddress", "customerPhone", "receiverPhone", "receiverAddress",
                "goodsName", "goodsType", "goodsRemark", "payeeName", "bankName", "bankCardNo",
                "remark", "preOrderNo", "packageType")) {
            map.put(key, new FilterField(camelToSnake(key), FilterType.TEXT));
        }
        // 枚举多选
        for (String key : List.of("status", "bizType", "payMethod", "deliveryMethod",
                "needReceipt", "waitNotice", "routeEndType")) {
            map.put(key, new FilterField(camelToSnake(key), FilterType.ENUM_MULTI));
        }
        // 数值区间
        for (String key : List.of("quantity", "weight", "volume", "freightFee", "deliveryFee",
                "pickupFee", "otherFee", "receiptFee", "makeBillFee", "insuranceFee",
                "declaredValue", "totalFee", "cashBack", "oweBack", "rebateTotal", "validFreight",
                "prepaidFee", "collectFee", "monthlyFee", "returnFee", "codAmount",
                "receiptCount", "routeNodeCount", "childSeq")) {
            map.put(key, new FilterField(camelToSnake(key), FilterType.NUMBER_RANGE));
        }
        // 日期区间
        map.put("createTime", new FilterField("create_time", FilterType.DATETIME_RANGE));
        // 网点/机构 id
        for (String key : List.of("destHubId", "originHubId", "deptId")) {
            map.put(key, new FilterField(camelToSnake(key), FilterType.ID));
        }
        return Map.copyOf(map);
    }

    private static Map<String, String> buildSortFields() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("waybillNo", "waybill_no");
        map.put("status", "status");
        map.put("relatedNo", "related_no");
        map.put("customerName", "customer_name");
        map.put("destName", "dest_name");
        map.put("destHubName", "dest_hub_name");
        map.put("senderName", "sender_name");
        map.put("receiverName", "receiver_name");
        map.put("bizType", "biz_type");
        map.put("routeName", "route_name");
        map.put("quantity", "quantity");
        map.put("weight", "weight");
        map.put("volume", "volume");
        map.put("payMethod", "pay_method");
        map.put("freightFee", "freight_fee");
        map.put("declaredValue", "declared_value");
        map.put("validFreight", "valid_freight");
        map.put("codAmount", "cod_amount");
        map.put("createByName", "create_by_name");
        map.put("createTime", "create_time");
        map.put("totalFee", "total_fee");
        map.put("deliveryFee", "delivery_fee");
        map.put("pickupFee", "pickup_fee");
        map.put("otherFee", "other_fee");
        map.put("receiptFee", "receipt_fee");
        map.put("makeBillFee", "make_bill_fee");
        map.put("insuranceFee", "insurance_fee");
        map.put("cashBack", "cash_back");
        map.put("oweBack", "owe_back");
        map.put("rebateTotal", "rebate_total");
        map.put("prepaidFee", "prepaid_fee");
        map.put("collectFee", "collect_fee");
        map.put("monthlyFee", "monthly_fee");
        map.put("returnFee", "return_fee");
        map.put("receiptCount", "receipt_count");
        map.put("childSeq", "child_seq");
        map.put("routeNodeCount", "route_node_count");
        return Map.copyOf(map);
    }

    private static String camelToSnake(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private enum FilterType {
        TEXT, ENUM_MULTI, NUMBER_RANGE, DATETIME_RANGE, ID
    }

    private record FilterField(String column, FilterType type) {
    }
}
