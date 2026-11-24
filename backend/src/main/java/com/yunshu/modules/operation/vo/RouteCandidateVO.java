package com.yunshu.modules.operation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 目的地 × 路由候选：目的地搜索与线路切换共用同一结构 */
@Data
public class RouteCandidateVO {

    private String chainText;

    /** 节点数 = 链路机构节点 + 终点区划节点（含起点） */
    private Integer nodeCount;

    /** 1=直达 2=外转 */
    private Integer endType;

    /** 末段路由是否提供自提 */
    private Boolean pickupEnabled;

    /** 末段路由是否提供送货 */
    private Boolean deliveryEnabled;

    /** 提供服务展示文案：自提 / 送货 / 自提+送货 */
    private String provideServices;

    /** 区域匹配深度：3=区县 2=市 1=省 */
    private Integer matchLevel;

    private BigDecimal totalHours;

    private BigDecimal totalMileage;

    private Boolean recommended;

    /** 目的地存在但没有任何可行路由时为 true；此时 nodes/routeKeys 为空 */
    private Boolean noRoute;

    /** 链路指纹，按顺序 "routeScope:routeId" */
    private List<String> routeKeys;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String districtCode;

    private String districtName;

    private String destName;

    private Long destHubId;

    private String destHubName;

    private List<Node> nodes;

    @Data
    public static class Node {

        private Integer seq;

        /** 1=短驳/干线 2=外转/直达 */
        private Integer routeScope;

        private Long routeId;

        private String routeCode;

        private Integer routeCategory;

        private Integer transferType;

        private Long fromDeptId;

        private String fromDeptName;

        private Long toDeptId;

        private String toDeptName;

        private String destRegionCode;

        private String destRegionName;

        private Long carrierId;

        private String carrierName;

        private String warehouseArea;

        private BigDecimal mileage;

        private BigDecimal transitHours;
    }
}
