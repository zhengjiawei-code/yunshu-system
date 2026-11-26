package com.yunshu.modules.transport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/** 保存运单毛利公式（#39）：版本号乐观锁，至少 1 个收入项。 */
@Data
public class GrossFormulaSaveRequest {

    private Integer version;

    private Boolean rebateDeduct;

    @Valid
    @NotEmpty(message = "公式不能为空")
    private List<Item> items;

    private String summary;

    @Data
    public static class Item {
        @NotBlank(message = "费用编码不能为空")
        private String feeCode;
        private String name;
        @Pattern(regexp = "add|sub", message = "方向只能为 add 或 sub")
        private String side;
    }
}
