package com.yunshu.modules.operation.vo;

import com.yunshu.modules.operation.entity.BizWaybill;
import lombok.Data;

import java.util.List;

/** 改单结果：更新后的运单 + 本次字段级差异 */
@Data
public class WaybillAmendVO {

    private BizWaybill waybill;

    private List<WaybillFieldChangeVO> changes;
}
