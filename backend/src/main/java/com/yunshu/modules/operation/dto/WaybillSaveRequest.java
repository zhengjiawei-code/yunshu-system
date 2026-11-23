package com.yunshu.modules.operation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 开单保存请求（Tab1 直接开单）。
 * 刻意不接收：运单号、客户字段、始发/运达网点、路由名称/节点数、派生金额，
 * 这些一律由服务端按规则生成或计算，客户端传值即使携带也不落库。
 */
@Data
public class WaybillSaveRequest {

    /** 开单网点（分拨中心/网点 id）；空则取当前机构。服务端校验管理范围、启用、allow_ship */
    private Long originDeptId;

    private String relatedNo;

    private Long salesId;

    /** 业务员姓名：无用户 id 时按手填快照落库 */
    private String salesName;

    /** 1=整车 2=零担，默认零担 */
    private Integer bizType;

    /** 目的地区县编码（唯一目的地判定依据） */
    private String destDistrictCode;

    /** 选中路由指纹：按顺序的 "routeScope:routeId"，用于保存时与二次匹配结果比对 */
    private List<String> routeKeys;

    private String senderName;

    private String senderPhone;

    /** 发货单位：可空 */
    private String senderCompany;

    private String senderAddress;

    private String receiverName;

    private String receiverPhone;

    /** 收货单位：可空 */
    private String receiverCompany;

    /** 收货地区：三码需同时提供，服务端校验层级并派生名称；与收货详细地址不做强校验 */
    private String receiverProvinceCode;

    private String receiverCityCode;

    private String receiverDistrictCode;

    private String receiverAddress;

    /** 货物明细行；服务端按行落子表并汇总覆盖主表 */
    private List<GoodsItem> goodsList;

    /** 以下三量仅用于篡改检测：若客户端传值，必须等于货物明细合计，否则拒绝 */
    private Integer quantity;

    private BigDecimal weight;

    private BigDecimal volume;

    /** 1现付 2到付 3月结 4回付 5多笔付；无默认值，未选直接拒绝 */
    private Integer payMethod;

    private BigDecimal freightFee;

    private BigDecimal deliveryFee;

    private BigDecimal pickupFee;

    private BigDecimal otherFee;

    private BigDecimal receiptFee;

    private BigDecimal makeBillFee;

    private BigDecimal insuranceFee;

    /** 声明价值：手工录入，不参与任何合计 */
    private BigDecimal declaredValue;

    private BigDecimal cashBack;

    private BigDecimal oweBack;

    /** 仅多笔付使用；单笔付由服务端覆盖 */
    private BigDecimal prepaidFee;

    private BigDecimal collectFee;

    private BigDecimal monthlyFee;

    private BigDecimal returnFee;

    /** 1=送货 2=自提 3=大车直送 */
    private Integer deliveryMethod;

    private BigDecimal codAmount;

    private String payeeName;

    private String bankName;

    private String bankCardNo;

    private Integer needReceipt;

    private Integer receiptCount;

    private Integer waitNotice;

    private String remark;

    @Data
    public static class GoodsItem {

        private String goodsName;

        private String goodsType;

        private String packageType;

        private Integer quantity;

        private BigDecimal weight;

        private BigDecimal volume;

        private String goodsRemark;
    }
}
