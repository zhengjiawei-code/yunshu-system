package com.yunshu.modules.system.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 路由授权分公司，对应 sys_route_company；company_dept_id=0 表示全部分公司（动态） */
@Data
@TableName("sys_route_company")
public class SysRouteCompany {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1=短驳/干线 2=外转/直达 */
    private Integer routeScope;

    private Long routeId;

    private Long companyDeptId;

    /** 1=创建自动 2=手动 3=总部全部 */
    private Integer grantSource;

    private Long grantBy;

    private LocalDateTime grantTime;

    private String remark;

    private LocalDateTime createTime;
}
