package com.yunshu.modules.operation.vo;

import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.entity.BizWaybillGoods;
import com.yunshu.modules.operation.entity.BizWaybillLog;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import lombok.Data;

import java.util.List;

/** 运单详情：主表字段 + 路由节点快照 + 货物明细行 + 操作日志 + 改单记录；越权时返回 403 */
@Data
public class WaybillDetailVO {

    private BizWaybill waybill;

    private List<BizWaybillRouteNode> nodes;

    private List<BizWaybillGoods> goods;

    /** 操作日志（挂母单，时间倒序） */
    private List<BizWaybillLog> logs;

    /** 改单记录（action_type=6，时间倒序），详情页「改单信息」tab 直接消费 */
    private List<BizWaybillLog> amendLogs;

    /** 有效子单数；子单为 0 */
    private Integer childCount;
}
