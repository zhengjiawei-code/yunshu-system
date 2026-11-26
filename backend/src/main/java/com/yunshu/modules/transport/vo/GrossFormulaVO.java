package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 运单毛利公式配置（#38）：公式 + 候选变量 + 变更历史。 */
@Data
public class GrossFormulaVO {

    private Integer version;

    private Boolean rebateDeduct;

    private List<Item> items;

    private String updatedBy;

    private String updatedAt;

    private String formulaText;

    private List<History> history;

    private List<Candidate> incomeCandidates;

    private List<Candidate> costCandidates;

    @Data
    public static class Item {
        private String feeCode;
        private String name;
        private String side;
    }

    @Data
    public static class History {
        private Integer version;
        private String updatedBy;
        private String updatedAt;
        private String summary;
    }

    @Data
    public static class Candidate {
        private String feeCode;
        private String feeName;
        private String side;
        private String sourceField;
        private Boolean readonly;
    }
}
