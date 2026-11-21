package com.yunshu.modules.system.carrier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 承运商表，对应 sys_carrier；豁免数据权限，可见性由服务层按管理范围显式过滤 */
@Data
@TableName("sys_carrier")
public class SysCarrier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String carrierCode;

    private String carrierName;

    private String contact;

    private String phone;

    private Boolean status;

    private Integer sortOrder;

    private String remark;

    /** 归属机构：新增时取当前登录人主机构，页面只读 */
    private Long deptId;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
