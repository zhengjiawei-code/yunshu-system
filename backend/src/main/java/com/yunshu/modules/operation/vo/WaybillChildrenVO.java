package com.yunshu.modules.operation.vo;

import com.yunshu.modules.operation.entity.BizWaybill;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 子单清单：母单信息、子单列表、母单与子单三量合计（不一致时前端红标） */
@Data
public class WaybillChildrenVO {

    private Long parentId;

    private String parentNo;

    private Map<String, BigDecimal> parentTotals;

    private Map<String, BigDecimal> childTotals;

    private List<BizWaybill> children;
}
