package com.yunshu.modules.system.route.vo;

import lombok.Data;

/**
 * 区划库条目：供外转/直达“加入路由”挑选，带当前网点的配置状态。
 */
@Data
public class RegionLibraryVO {

    private String code;

    private String name;

    /** 2=市 3=区县 4=乡级 */
    private Integer level;

    private String parentCode;

    private String fullName;

    private String provinceName;

    private String cityName;

    private String districtName;

    /** 当前网点是否已存在本公司可用路由 */
    private Boolean configured;

    /** 已配置时的路由ID，便于直接定位 */
    private Long routeId;

    /** 已配置时的路由类型：1=直达 2=外转 */
    private Integer transferType;
}
