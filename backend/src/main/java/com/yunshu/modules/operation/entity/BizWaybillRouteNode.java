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

/** 运单路由节点快照，对应 biz_waybill_route_node；保存开单时与运单同事务写入 */
@Data
@TableName("biz_waybill_route_node")
public class BizWaybillRouteNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long waybillId;

    private Integer seq;

    /** 1=短驳/干线（sys_route） 2=外转/直达（sys_route_transfer） */
    private Integer routeScope;

    private Long routeId;

    private String routeCode;

    /** 短驳/干线：1=短驳 2=干线 */
    private Integer routeCategory;

    /** 外转/直达：1=直达 2=外转 */
    private Integer transferType;

    private Long fromDeptId;

    private String fromDeptName;

    private Long toDeptId;

    private String toDeptName;

    private String destRegionCode;

    private String destRegionName;

    private Long carrierId;

    private String carrierName;

    private String warehouseArea;

    private BigDecimal mileage;

    private BigDecimal transitHours;

    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
