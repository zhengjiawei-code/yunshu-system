package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 外转/直达路由新增与编辑 */
@Data
public class TransferSaveRequest {

    /** 1=直达 2=外转 */
    @NotNull(message = "请选择路由类型")
    private Integer transferType;

    @NotNull(message = "请选择适用网点")
    private Long applyDeptId;

    @NotBlank(message = "请选择目的地")
    private String regionCode;

    private String routeName;

    /** 外转时效（小时），选填 */
    private BigDecimal transitHours;

    /** 外转里程（公里），选填 */
    private BigDecimal mileage;

    /** 仅外转可填；直达由服务端强制清空 */
    private Long carrierId;

    private Boolean status;

    private Integer sortOrder;

    private String remark;
}
