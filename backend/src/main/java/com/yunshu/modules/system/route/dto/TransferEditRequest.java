package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 外转/直达路由编辑：仅允许修改名称、承运商、状态、排序、备注。
 * 目的地、编码、类型由系统维护，禁止在编辑接口中变更（类型走切换接口）。
 */
@Data
public class TransferEditRequest {

    @Size(max = 128, message = "路由名称不能超过 128 个字符")
    private String routeName;

    /** 仅外转可挂载；直达忽略并强制清空 */
    private Long carrierId;

    private Boolean status;

    /** 是否提供自提；为空表示保持原值 */
    private Boolean pickupEnabled;

    /** 是否提供送货；为空表示保持原值 */
    private Boolean deliveryEnabled;

    /** 别名；为空串拒绝，不传表示保持原值 */
    @Size(max = 64, message = "别名不能超过 64 个字符")
    private String alias;

    @Min(value = 0, message = "排序号不能小于 0")
    @Max(value = 9999, message = "排序号不能大于 9999")
    private Integer sortOrder;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
