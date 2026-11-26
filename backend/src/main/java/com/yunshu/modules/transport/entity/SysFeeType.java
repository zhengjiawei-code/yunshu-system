package com.yunshu.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 费用类型配置，对应 sys_fee_type（V31）。
 * 总部全局配置，数据权限豁免；收入四项 INC01-INC04 为代码级虚拟行，不落本表。
 */
@Data
@TableName("sys_fee_type")
public class SysFeeType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String feeCode;

    private String feeName;

    /** 1=收入 2=成本 3=代收 4=代付 */
    private Integer feeCategory;

    private Boolean enabled;

    private Boolean profitEnabled;

    /** 1=收入(加项) 2=成本(减项)；代收/代付为空 */
    private Integer profitDirection;

    private String sourceField;

    private Integer sortOrder;

    private String remark;

    private Long deptId;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
