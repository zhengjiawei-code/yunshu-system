package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 车次列表查询参数。日期/区间按 start/end 传入，状态多选逗号分隔。
 */
@Data
public class TripListQuery {

    private int page = 1;

    private int pageSize = 20;

    private String sortBy;

    private String sortOrder;

    private String filterModes;

    private String tripNo;

    private String tripName;

    private List<Integer> status = new ArrayList<>();

    private Long originDeptId;

    private Long destDeptId;

    private String plateNo;

    private String driverName;

    private String planDepartStart;

    private String planDepartEnd;

    private String departStart;

    private String departEnd;

    private String planArriveStart;

    private String planArriveEnd;

    private String actualArriveStart;

    private String actualArriveEnd;

    private String createStart;

    private String createEnd;

    public static TripListQuery from(Map<String, String> params) {
        TripListQuery query = new TripListQuery();
        if (params == null || params.isEmpty()) {
            return query;
        }
        params.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String text = value.trim();
            switch (key) {
                case "page" -> query.setPage(parseInt(text, 1));
                case "pageSize" -> query.setPageSize(parseInt(text, 20));
                case "sortBy" -> query.setSortBy(text);
                case "sortOrder" -> query.setSortOrder(text);
                case "filterModes" -> query.setFilterModes(text);
                case "tripNo" -> query.setTripNo(text);
                case "tripName" -> query.setTripName(text);
                case "status", "status[]" -> query.setStatus(parseInts(text));
                case "originDeptId" -> query.setOriginDeptId(parseLong(text));
                case "destDeptId" -> query.setDestDeptId(parseLong(text));
                case "plateNo" -> query.setPlateNo(text);
                case "driverName" -> query.setDriverName(text);
                case "planDepartStart" -> query.setPlanDepartStart(text);
                case "planDepartEnd" -> query.setPlanDepartEnd(text);
                case "departStart" -> query.setDepartStart(text);
                case "departEnd" -> query.setDepartEnd(text);
                case "planArriveStart" -> query.setPlanArriveStart(text);
                case "planArriveEnd" -> query.setPlanArriveEnd(text);
                case "actualArriveStart" -> query.setActualArriveStart(text);
                case "actualArriveEnd" -> query.setActualArriveEnd(text);
                case "createStart" -> query.setCreateStart(text);
                case "createEnd" -> query.setCreateEnd(text);
                default -> {
                }
            }
        });
        return query;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return null;
        }
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
                // 非法状态码忽略
            }
        }
        return result;
    }
}
