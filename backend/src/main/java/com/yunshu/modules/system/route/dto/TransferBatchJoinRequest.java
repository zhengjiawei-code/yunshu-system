package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 从区划库批量加入外转/直达路由（默认外转） */
@Data
public class TransferBatchJoinRequest {

    @NotNull(message = "请先选择配置网点")
    private Long applyDeptId;

    @NotEmpty(message = "请选择要加入的区划")
    @Size(max = 200, message = "单次最多加入 200 条区划")
    private List<String> regionCodes;

    /** 是否提供自提，未传默认 1 */
    private Boolean pickupEnabled;

    /** 是否提供送货，未传默认 1 */
    private Boolean deliveryEnabled;

    /** 别名，未传或为空时后端取目的地全称 */
    @Size(max = 64, message = "别名不能超过 64 个字符")
    private String alias;
}
