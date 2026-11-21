package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.util.List;

@Data
public class TransferPageVO {

    private List<RouteTransferVO> records;

    private Integer total;
}
