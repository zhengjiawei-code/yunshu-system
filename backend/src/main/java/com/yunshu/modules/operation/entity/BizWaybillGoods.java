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

/** 运单货物明细子表，对应 biz_waybill_goods；行汇总必须等于主表件数/重量/体积 */
@Data
@TableName("biz_waybill_goods")
public class BizWaybillGoods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long waybillId;

    private Integer seq;

    private String goodsName;

    private String goodsType;

    private String packageType;

    private Integer quantity;

    private BigDecimal weight;

    private BigDecimal volume;

    private String goodsRemark;

    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
