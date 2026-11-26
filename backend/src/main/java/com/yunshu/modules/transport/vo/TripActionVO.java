package com.yunshu.modules.transport.vo;

import lombok.Data;

/** 发车/取消发车/关闭的状态流转结果（#13/#14/#17）。 */
@Data
public class TripActionVO {

    private Long tripId;

    private Integer tripStatus;

    private String tripStatusName;

    /** 受影响的配载单元数 */
    private Integer affectedUnits;

    /** 受影响的运单数 */
    private Integer affectedWaybills;

    /** 受影响的子车次数 */
    private Integer affectedDests;

    private String departTime;
}
