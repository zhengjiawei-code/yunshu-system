package com.yunshu.modules.system.carrier.vo;

import lombok.Data;

import java.util.List;

@Data
public class CarrierPageVO {

    private List<CarrierVO> records;

    private Integer total;
}
