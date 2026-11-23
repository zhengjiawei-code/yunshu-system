package com.yunshu.modules.operation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预开单，对应 biz_pre_waybill；本阶段仅只读列表与详情 */
@Data
@TableName("biz_pre_waybill")
public class BizPreWaybill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String preOrderNo;

    /** 10待接单 20已接单 30已揽收 90已撤单 */
    private Integer status;

    private Long customerId;

    private String customerName;

    private String customerPhone;

    private String senderName;

    private String senderPhone;

    private String senderAddress;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String destProvinceCode;

    private String destProvinceName;

    private String destCityCode;

    private String destCityName;

    private String destDistrictCode;

    private String destDistrictName;

    private String destName;

    private String goodsName;

    private Integer quantity;

    private BigDecimal weight;

    private BigDecimal volume;

    private String goodsRemark;

    private LocalDateTime expectPickupTime;

    private LocalDateTime submitTime;

    private Long acceptBy;

    private String acceptByName;

    private LocalDateTime acceptTime;

    private String waybillNo;

    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    private String deptName;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
