package com.yunshu.modules.transport.vo;

import lombok.Data;

/** 到站/卸车/取消到站/取消卸车的结果（#34/#35/#15a/#16a）。 */
@Data
public class DestActionVO {

    private Long tripId;

    private Long destId;

    private Integer tripStatus;

    private String tripStatusName;

    private Integer destStatus;

    private Integer affectedWaybills;

    private Integer affectedUnits;

    private Integer invalidFeeCount;
}
