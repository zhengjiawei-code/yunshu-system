package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 短驳/干线路由新增与编辑；干线必填库区/里程/时效/联系人/电话/客服/客服电话 */
@Data
public class RouteSaveRequest {

    @NotNull(message = "请选择路由类型")
    private Integer routeCategory;

    @NotNull(message = "请选择始发网点")
    private Long originDeptId;

    @NotNull(message = "请选择到达网点")
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
