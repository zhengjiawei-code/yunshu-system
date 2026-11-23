package com.yunshu.modules.operation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 开单保存返回：运单号 + 服务端实算的派生金额 + 路由派生网点 */
@Data
public class WaybillSaveVO {

    private Long id;

    private String waybillNo;

    private Integer status;

    private Integer payMethod;

    private BigDecimal totalFee;

    private BigDecimal rebateTotal;

    private BigDecimal validFreight;

    private BigDecimal prepaidFee;

    private BigDecimal collectFee;

    private BigDecimal monthlyFee;

    private BigDecimal returnFee;

    private Long customerId;

    private String customerName;

    private Long originHubId;

    private String originHubName;

    private Long destHubId;

    private String destHubName;

    private String routeName;

    private Integer routeNodeCount;

    private Integer routeEndType;

    private String destName;

    private LocalDateTime createTime;
}
