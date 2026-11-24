package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 短驳/干线路由新增与编辑；仅路由类型/始发网点/运达网点必填，其余选填 */
@Data
public class RouteSaveRequest {

    @NotNull(message = "请选择路由类型")
    private Integer routeCategory;

    @NotNull(message = "请选择始发网点")
    private Long originDeptId;

    @NotNull(message = "请选择运达网点")
    private Long destDeptId;

    private String routeName;

    private String warehouseArea;

    private BigDecimal mileage;

    private BigDecimal transitHours;

    private String contactName;

    private String contactPhone;

    private String serviceName;

    private String servicePhone;

    private Boolean status;

    private Integer sortOrder;

    private String remark;
}
