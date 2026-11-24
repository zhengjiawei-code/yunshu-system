package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RouteTransferVO {

    private Long id;

    private String routeCode;

    private String routeName;

    private Integer transferType;

    /** 是否提供自提 */
    private Boolean pickupEnabled;

    /** 是否提供送货 */
    private Boolean deliveryEnabled;

    private Long applyDeptId;

    private String applyDeptName;

    private String regionCode;

    private Integer regionLevel;

    private String regionFullName;

    /** 目的地四级区划名称，按 regionCode 前缀实时取自区划表，不落快照 */
    private String destProvinceName;

    private String destCityName;

    private String destDistrictName;

    private String destTownName;

    /** 目的地最末级区划名称（列表显示与筛选口径），园区划层级取对应名称 */
    private String destLeafName;

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
