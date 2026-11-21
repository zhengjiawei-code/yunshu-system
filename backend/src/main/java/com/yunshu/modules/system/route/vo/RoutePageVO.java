package com.yunshu.modules.system.route.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoutePageVO {

    private List<RouteVO> records;

    private Integer total;
}
