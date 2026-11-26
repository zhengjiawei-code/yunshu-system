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

/**
 * 运单主表，对应 biz_waybill（母单与子单同表）。
 * 派生金额一律由服务端 WaybillAmountCalculator 计算后写入，客户端传值不落库。
 */
@Data
@TableName("biz_waybill")
public class BizWaybill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String waybillNo;

    private String relatedNo;

    private String preOrderNo;

    private Long parentId;

    private String parentNo;

    /** 0=母单 1=子单 */
    private Integer isChild;

    private Integer childSeq;

    /** 10待揽收 20已揽收 25已拆单 30已装车 40运输中 50已到达 55部分到达 60已签收 70异常 90已作废 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Long deptId;

    private String deptName;

    private Long salesId;

    private String salesName;

    /** 1=整车 2=零担 */
    private Integer bizType;

    private String destProvinceCode;

    private String destProvinceName;

    private String destCityCode;

    private String destCityName;

    private String destDistrictCode;

    private String destDistrictName;

    private String destName;

    private Long originHubId;

    private String originHubName;

    private Long destHubId;

    private String destHubName;

    /** 当前库存网点ID（v1.3 配载定位） */
    private Long currentHubId;

    /** 当前库存网点名称快照 */
    private String currentHubName;

    /** 已完成路由节点段序号，0=仍在始发网点 */
    private Integer currentNodeSeq;

    /** 库存落入当前网点时间 */
    private LocalDateTime inventoryTime;

    /** 0待配载 1已配载 2已发车 3已到站 4已卸车 */
    private Integer loadStatus;

    private String routeName;

    private Integer routeNodeCount;

    /** 1=直达 2=外转 */
    private Integer routeEndType;

    private Long customerId;

    private String customerName;

    private String customerPhone;

    private String senderName;

    private String senderPhone;

    private String senderCompany;

    private String senderAddress;

    private String receiverName;

    private String receiverPhone;

    private String receiverCompany;

    private String receiverProvinceCode;

    private String receiverProvinceName;

    private String receiverCityCode;

    private String receiverCityName;

    private String receiverDistrictCode;

    private String receiverDistrictName;

    private String receiverAddress;

    private String goodsName;

    private String goodsType;

    private String packageType;

    private Integer quantity;

    private BigDecimal weight;

    private BigDecimal volume;

    private String goodsRemark;

    /** 1现付 2到付 3月结 4回付 5多笔付 */
    private Integer payMethod;

    private BigDecimal freightFee;

    private BigDecimal deliveryFee;

    private BigDecimal pickupFee;

    private BigDecimal otherFee;

    private BigDecimal receiptFee;

    private BigDecimal makeBillFee;

    private BigDecimal insuranceFee;

    private BigDecimal declaredValue;

    private BigDecimal totalFee;

    private BigDecimal prepaidFee;

    private BigDecimal collectFee;

    private BigDecimal monthlyFee;

    private BigDecimal returnFee;

    private BigDecimal cashBack;

    private BigDecimal oweBack;

    private BigDecimal rebateTotal;

    private BigDecimal validFreight;

    /** 1=送货，2=自提，3=大车直送（不受线路服务能力限制） */
    private Integer deliveryMethod;

    private BigDecimal codAmount;

    private String payeeName;

    private String bankName;

    private String bankCardNo;

    private Integer needReceipt;

    private Integer receiptCount;

    private Integer waitNotice;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private String createByName;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 列表派生字段：有效子单数，非库表列 */
    @TableField(exist = false)
    private Integer childCount;

    /** 列表派生字段：是否存在子单，非库表列 */
    @TableField(exist = false)
    private Boolean hasChildren;
}
