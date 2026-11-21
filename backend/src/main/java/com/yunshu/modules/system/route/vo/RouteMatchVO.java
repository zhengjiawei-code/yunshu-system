package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 开单匹配结果：首条 recommended=true；无候选返回可识别文案供开单页阻断 */
@Data
public class RouteMatchVO {

    private Boolean matched;

    private String message;

    private Long recommendedId;

    private List<RouteMatchItemVO> records;

    @Data
    public static class RouteMatchItemVO {

        private Integer categoryRank;

        private String categoryLabel;

        private Integer hops;

        private BigDecimal totalTransitHours;

        private BigDecimal totalMileage;

        private Boolean recommended;

        private List<RouteMatchNodeVO> nodes;
    }

    @Data
    public static class RouteMatchNodeVO {

        private Integer routeScope;

        private Long routeId;

        private String routeCode;

        private Integer category;

        private Long fromDeptId;

        private String fromDeptName;

        private Long toDeptId;

        private String toDeptName;

        private String routeName;

        private String contactName;

        private String contactPhone;

        private BigDecimal transitHours;

        private BigDecimal mileage;

        private String warehouseArea;

        private Long carrierId;

        private String carrierName;

        private String serviceName;

        private String servicePhone;
    }
}
