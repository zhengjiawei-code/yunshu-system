package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RouteTransferVO {

    private Long id;

    private String routeCode;

    private String routeName;

    private Integer transferType;

    private Long applyDeptId;

    private String applyDeptName;

    private String regionCode;

    private Integer regionLevel;

    private String regionFullName;

    private Long carrierId;

    private String carrierName;

    private Long ownerCompanyId;

    private String ownerCompanyName;

    private Boolean status;

    private Integer sortOrder;

    private String remark;

    private String createByName;

    private LocalDateTime createTime;

    private String updateByName;

    private LocalDateTime updateTime;

    private String grantScope;

    private Integer grantCount;

    private Boolean editable;

    private Boolean deletable;
}
