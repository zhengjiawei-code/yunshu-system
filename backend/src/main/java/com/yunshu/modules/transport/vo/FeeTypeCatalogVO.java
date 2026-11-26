package com.yunshu.modules.transport.vo;

import lombok.Data;

/** 新增费用下拉目录（#27）：按类别返回可选项。 */
@Data
public class FeeTypeCatalogVO {

    private String feeCode;

    private String feeName;

    private Integer feeCategory;

    /** 默认方向：1=收入(加) 2=成本(减)，代收/代付为空 */
    private Integer defaultDirection;

    /** 是否已配置（已落 sys_fee_type） */
    private Boolean configured;
}
