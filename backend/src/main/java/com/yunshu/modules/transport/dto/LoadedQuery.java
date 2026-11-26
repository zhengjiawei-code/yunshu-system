package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.util.Map;

/** 已配载列表查询。 */
@Data
public class LoadedQuery {

    private int page = 1;
    private int pageSize = 50;
    private String sortBy;
    private String sortOrder;
    private String waybillNo;
    private String parentNo;
    private String customerName;
    private String receiverName;
    private String destName;
    private Long tripDestId;
    private Integer loadStatus;

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
                case "receiverName" -> q.receiverName = t;
                case "destName" -> q.destName = t;
                case "tripDestId" -> q.tripDestId = longOf(t);
                case "loadStatus" -> q.loadStatus = intOf(t, 0);
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
}
