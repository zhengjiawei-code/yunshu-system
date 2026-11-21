package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RouteGrantVO {

    private Long id;

    private Long companyDeptId;

    private String companyName;

    private Integer grantSource;

    private String grantSourceName;

    private Long grantBy;

    private String grantByName;

    private LocalDateTime grantTime;

    private String remark;
}
