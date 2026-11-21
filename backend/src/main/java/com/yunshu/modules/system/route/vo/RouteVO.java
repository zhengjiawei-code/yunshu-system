package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RouteVO {

    private Long id;

    private String routeCode;

    private Integer routeCategory;

    private Long originDeptId;

    private String originDeptName;

    private Long destDeptId;

    private String destDeptName;

    private String routeName;

    private String warehouseArea;

    private BigDecimal mileage;

    private BigDecimal transitHours;

    private String contactName;

    private String contactPhone;

    private String serviceName;

    private String servicePhone;

    private Long ownerCompanyId;

    private String ownerCompanyName;

    private Boolean status;

    private Integer sortOrder;

    private String remark;

    private String createByName;

    private LocalDateTime createTime;

    private String updateByName;

    private LocalDateTime updateTime;

    /** ALL=全部（0 号行）；COUNT=按公司授权；分公司视角为空 */
    private String grantScope;

    private Integer grantCount;

    /** 当前账号是否可编辑（总部或创建公司） */
    private Boolean editable;

    /** 当前账号是否可取消路由（已按授权关系计算） */
    private Boolean deletable;
}
