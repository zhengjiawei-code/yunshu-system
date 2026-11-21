package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.util.List;

/** 路由使用情况：供移除确认弹窗判定与展示 */
@Data
public class RouteUsageVO {

    private Long id;

    private String routeCode;

    private String routeName;

    private Long ownerCompanyId;

    private String ownerCompanyName;

    private Long currentCompanyId;

    /** 当前账号是否总部 */
    private Boolean hq;

    /** 当前账号是否为创建公司 */
    private Boolean owner;

    /** 是否允许直接移除 */
    private Boolean canRemove;

    /** 是否允许取消本公司授权 */
    private Boolean canRevoke;

    /** 不允许移除时的原因；可移除时为空 */
    private String blockReason;

    /** 当前使用该路由的公司清单（依据授权关系表，排除已取消/失效） */
    private List<RouteGrantVO> companies;
}
