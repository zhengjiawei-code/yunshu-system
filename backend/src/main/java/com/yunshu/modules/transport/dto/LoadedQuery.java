package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 已配载列表查询：全部条件在 SQL 层过滤，分页在过滤之后。 */
@Data
public class LoadedQuery {

    private int page = 1;
    private int pageSize = 50;
    private String sortBy;
    private String sortOrder;
    private String waybillNo;
    private String parentNo;
    private String customerName;
    private String senderName;
    private String receiverName;
    private String destName;
    private Long tripDestId;
    private Long destDeptId;
    private Integer loadStatus;
    private List<Integer> status = new ArrayList<>();
    private List<Integer> payMethod = new ArrayList<>();
    private Integer settleStatus;
    private Integer unitType;
    private Integer quantityMin;
    private Integer quantityMax;
    private BigDecimal weightMin;
    private BigDecimal weightMax;
    private BigDecimal volumeMin;
    private BigDecimal volumeMax;
    private BigDecimal loadFeeMin;
    private BigDecimal loadFeeMax;
    private BigDecimal loadRebateMin;
    private BigDecimal loadRebateMax;
    private BigDecimal totalFeeMin;
    private BigDecimal totalFeeMax;
    private BigDecimal rebateTotalMin;
    private BigDecimal rebateTotalMax;
    private String loadStart;
    private String loadEnd;

    public static LoadedQuery from(Map<String, String> params) {
        LoadedQuery q = new LoadedQuery();
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
                case "pageSize" -> q.pageSize = intOf(t, 50);
                case "sortBy" -> q.sortBy = t;
                case "sortOrder" -> q.sortOrder = t;
                case "waybillNo" -> q.waybillNo = t;
                case "parentNo" -> q.parentNo = t;
                case "customerName" -> q.customerName = t;
                case "senderName" -> q.senderName = t;
                case "receiverName" -> q.receiverName = t;
                case "destName" -> q.destName = t;
                case "tripDestId" -> q.tripDestId = longOf(t);
                case "destDeptId" -> q.destDeptId = longOf(t);
                case "loadStatus" -> q.loadStatus = intOf(t, 0);
                case "status", "status[]" -> q.status = intsOf(t);
                case "payMethod", "payMethod[]" -> q.payMethod = intsOf(t);
                case "settleStatus" -> q.settleStatus = intOf(t, -1);
                case "unitType" -> q.unitType = intOf(t, 0);
                case "quantityMin" -> q.quantityMin = intOf(t, 0);
                case "quantityMax" -> q.quantityMax = intOf(t, 0);
                case "weightMin" -> q.weightMin = decimalOf(t);
                case "weightMax" -> q.weightMax = decimalOf(t);
                case "volumeMin" -> q.volumeMin = decimalOf(t);
                case "volumeMax" -> q.volumeMax = decimalOf(t);
                case "loadFeeMin" -> q.loadFeeMin = decimalOf(t);
                case "loadFeeMax" -> q.loadFeeMax = decimalOf(t);
                case "loadRebateMin" -> q.loadRebateMin = decimalOf(t);
                case "loadRebateMax" -> q.loadRebateMax = decimalOf(t);
                case "totalFeeMin" -> q.totalFeeMin = decimalOf(t);
                case "totalFeeMax" -> q.totalFeeMax = decimalOf(t);
                case "rebateTotalMin" -> q.rebateTotalMin = decimalOf(t);
                case "rebateTotalMax" -> q.rebateTotalMax = decimalOf(t);
                case "loadStart" -> q.loadStart = t;
                case "loadEnd" -> q.loadEnd = t;
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

    private static BigDecimal decimalOf(String v) {
        try {
            return new BigDecimal(v);
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
