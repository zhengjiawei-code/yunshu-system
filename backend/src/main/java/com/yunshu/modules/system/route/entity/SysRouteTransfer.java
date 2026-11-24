package com.yunshu.modules.system.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 外转/直达路由，对应 sys_route_transfer；apply_dept_id 为业务字段，dept_id 仅审计锚点 */
@Data
@TableName("sys_route_transfer")
public class SysRouteTransfer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String routeCode;

    private String routeName;

    /** 1=直达 2=外转 */
    private Integer transferType;

    /** 是否提供自提：1=是 0=否 */
    private Boolean pickupEnabled;

    /** 是否提供送货：1=是 0=否 */
    private Boolean deliveryEnabled;

    /** 业务适用网点 */
    private Long applyDeptId;

    private String regionCode;

    private Integer regionLevel;

    private String regionFullName;

    /** 仅外转可填 */
    private Long carrierId;

    private Long ownerCompanyId;

    private Boolean status;

    private Integer sortOrder;

    private String remark;

    /** 审计锚点：创建人主机构，由服务层显式写入，禁止与 apply_dept_id 混用 */
    private Long deptId;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
