package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 待配载池查询。所有筛选先落 SQL，派生条件（可配单元/下一节点/剩余量）在服务层一次算足。 */
@Data
public class PoolQuery {

    private int page = 1;
    private int pageSize = 20;
    private String sortBy;
    private String sortOrder;
    private String filterModes;
    private String waybillNo;
    private Integer unitType;
    private List<Integer> status = new ArrayList<>();
    private String customerName;
    private String receiverName;
    private Long nextDeptId;
    private String destStation;
    private String inventoryStart;
    private String inventoryEnd;
    private String createStart;
    private String createEnd;
    private Integer quantityMin;
    private Integer quantityMax;
    private String weightMin;
    private String weightMax;
    private String volumeMin;
    private String volumeMax;

    public static PoolQuery from(Map<String, String> params) {
        PoolQuery q = new PoolQuery();
        if (params == null) {
            return q;
        }
        params.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            String t = v.trim();
            switch (k) {
                case "page" -> q.page = intOf(t, 1);
                case "pageSize" -> q.pageSize = intOf(t, 20);
                case "sortBy" -> q.sortBy = t;
                case "sortOrder" -> q.sortOrder = t;
                case "filterModes" -> q.filterModes = t;
                case "waybillNo" -> q.waybillNo = t;
                case "unitType" -> q.unitType = intOf(t, 0);
                case "status", "status[]" -> q.status = intsOf(t);
                case "customerName" -> q.customerName = t;
                case "receiverName" -> q.receiverName = t;
                case "nextDeptId" -> q.nextDeptId = longOf(t);
                case "destStation" -> q.destStation = t;
                case "inventoryStart" -> q.inventoryStart = t;
                case "inventoryEnd" -> q.inventoryEnd = t;
                case "createStart" -> q.createStart = t;
                case "createEnd" -> q.createEnd = t;
                case "quantityMin" -> q.quantityMin = intOf(t, 0);
                case "quantityMax" -> q.quantityMax = intOf(t, 0);
                case "weightMin" -> q.weightMin = t;
                case "weightMax" -> q.weightMax = t;
                case "volumeMin" -> q.volumeMin = t;
                case "volumeMax" -> q.volumeMax = t;
                default -> {
                }
            }
        });
        return q;
    }

    private static int intOf(String v, int d) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return d;
        }
    }

    private static Long longOf(String v) {
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Integer> intsOf(String v) {
        List<Integer> list = new ArrayList<>();
        if (v == null || v.isBlank()) {
            return list;
        }
        for (String p : v.split("[,，]")) {
            try {
                list.add(Integer.parseInt(p.trim()));
            } catch (Exception ignored) {
            }
        }
        return list;
    }
}
