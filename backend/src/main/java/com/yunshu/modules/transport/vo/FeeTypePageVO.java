package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.util.List;

/** 费用类型统一列表（#26）：收入 4 项只读虚拟行 + sys_fee_type 配置行。 */
@Data
public class FeeTypePageVO {

    private List<Row> rows;

    private Integer total;

    private Integer page;

    private Integer pageSize;

    private Counts counts;

    @Data
    public static class Row {
        private Long id;
        private String feeCode;
        private String feeName;
        private Integer feeCategory;
        private String feeCategoryName;
        private Boolean enabled;
        private Boolean profitEnabled;
        private Integer profitDirection;
        private String sourceField;
        private Integer sortOrder;
        private String remark;
        /** 收入内置四项为 true：不可编辑/启停，操作列显示「—」 */
        private Boolean readonly;
    }

    @Data
    public static class Counts {
        private Integer income;
        private Integer cost;
        private Integer collect;
        private Integer payable;
    }
}
