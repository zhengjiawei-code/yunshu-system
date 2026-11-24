package com.yunshu.modules.operation.vo;

import com.yunshu.modules.operation.entity.BizWaybill;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 运单列表：数据 + 总数 + 当前筛选条件下全部数据的合计 */
@Data
public class WaybillPageVO {

    private List<BizWaybill> data;

    private long total;

    private long page;

    private long pageSize;

    private Map<String, BigDecimal> summary;
}
