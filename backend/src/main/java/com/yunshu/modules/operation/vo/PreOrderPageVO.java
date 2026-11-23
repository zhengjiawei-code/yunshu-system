package com.yunshu.modules.operation.vo;

import com.yunshu.modules.operation.entity.BizPreWaybill;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 预开单只读列表：数据 + 总数 + 三量合计（当前筛选条件下的全部数据） */
@Data
public class PreOrderPageVO {

    private List<BizPreWaybill> data;

    private long total;

    private long page;

    private long pageSize;

    private Map<String, BigDecimal> summary;
}
