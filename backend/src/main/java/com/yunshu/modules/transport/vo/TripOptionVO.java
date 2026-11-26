package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 车次下拉选项：网点 / 车辆 / 司机 / 车型统一结构。 */
@Data
public class TripOptionVO {

    private Long id;

    private String name;

    private String type;

    private BigDecimal load;

    private BigDecimal volume;

    private String phone;

    private Boolean leaf;
}
