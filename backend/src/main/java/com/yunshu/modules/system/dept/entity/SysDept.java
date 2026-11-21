package com.yunshu.modules.system.dept.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机构表实体，对应 sys_dept；单表树，最多 5 级，id=1 为内置总部。
 */
@Data
@TableName("sys_dept")
public class SysDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级机构ID，0=顶级（仅内置总部） */
    private Long parentId;

    private String deptName;

    /** 机构编码，全局唯一，创建后不可修改 */
    private String deptCode;

    /** 1=总部 2=公司 3=部门 4=分拨中心 5=网点 */
    private Integer deptType;

    /** 机构性质：1=自营 2=加盟 3=合作；总部固定为 1（自营），不可修改 */
    private Integer deptNature;

    /** 是否末端：仅网点可为 true；纯业务标识，与结构上是否有下级无关 */
    private Boolean isLeaf;

    /** 能否发货：机构级业务能力，不随父机构继承 */
    private Boolean allowShip;

    /** 能否到货 */
    private Boolean allowArrival;

    /** 现付未付开单 */
    private Boolean allowPrepaidUnpaid;

    /** 到付未付签收 */
    private Boolean allowCollectUnpaidSign;

    private String leader;

    private String phone;

    private String address;

    /** 省编码，引用 sys_region.code（level=1） */
    private String provinceCode;

    /** 市编码，引用 sys_region.code（level=2） */
    private String cityCode;

    /** 区县编码，引用 sys_region.code（level=3） */
    private String districtCode;

    private Integer sortOrder;

    private Boolean status;

    private String remark;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
