package com.yunshu.modules.system.route.dto;

import lombok.Data;

/** 短驳/干线路由列表查询 */
@Data
public class RouteQuery {

    private String routeCode;

    private String routeName;

    private Integer routeCategory;

    private Long originDeptId;

    private Long destDeptId;

    private Boolean status;

    /** ALL=授权全部；PARTIAL=按公司授权 */
    private String grantScope;

    private String sortBy;

    private String sortOrder;

    private Integer pageNum;

    private Integer pageSize;
}
