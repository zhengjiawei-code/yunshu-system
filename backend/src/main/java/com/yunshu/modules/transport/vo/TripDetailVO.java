package com.yunshu.modules.transport.vo;

import com.yunshu.modules.transport.entity.BizTrip;
import lombok.Data;

import java.util.List;

/** 车次详情：主表 + 子车次 + 汇总。 */
@Data
public class TripDetailVO {

    private BizTrip trip;

    private List<TripDestVO> dests;

    private TripPageVO.Summary summary;
}
