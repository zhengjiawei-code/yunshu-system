package com.yunshu.modules.system.route.dto;

import lombok.Data;

/** 外转/直达路由列表查询 */
@Data
public class TransferQuery {

    private String routeCode;

    private String routeName;

    private Integer transferType;

    private Long applyDeptId;

    private String regionCode;

    /** 目的地级别：2=市 3=区县 4=乡级 */
    private Integer regionLevel;

    private Long carrierId;

    private Boolean status;

    private String grantScope;

    /** 修改人姓名（模糊匹配） */
    private String updateByName;

    private String sortBy;

    private String sortOrder;

    private Integer pageNum;

    private Integer pageSize;
}
