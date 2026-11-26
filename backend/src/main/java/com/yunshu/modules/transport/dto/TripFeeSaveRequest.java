package com.yunshu.modules.transport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 保存车次级成本项 + 全车分摊方式（#11）。 */
@Data
public class TripFeeSaveRequest {

    @NotNull(message = "分摊方式不能为空")
    @Min(value = 1, message = "分摊方式不合法")
    @Max(value = 3, message = "分摊方式不合法")
    private Integer allocMethod;

    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "费用编码不能为空")
        private String feeCode;

        @NotNull(message = "费用金额不能为空")
        @DecimalMin(value = "0", message = "费用金额不能为负数")
        private BigDecimal amount;
    }
}
