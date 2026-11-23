package com.yunshu.modules.operation.vo;

import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.entity.BizWaybillGoods;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import lombok.Data;

import java.util.List;

/** 运单详情：主表字段 + 路由节点快照 + 货物明细行；越权时查询结果为空并返回 403 */
@Data
public class WaybillDetailVO {

    private BizWaybill waybill;

    private List<BizWaybillRouteNode> nodes;

    private List<BizWaybillGoods> goods;
}
