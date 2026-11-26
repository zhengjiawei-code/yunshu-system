package com.yunshu.modules.transport.vo;

import lombok.Data;

import java.util.List;

/** 创建车次返回：主键、车次号与生成的子车次。 */
@Data
public class TripSaveVO {

    private Long id;

    private String tripNo;

    private List<TripDestVO> subTrips;
}
