package com.yunshu.modules.transport.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 保存车次车辆/司机信息（#10）；仅车次计划/装车配载状态可改，发车后锁定。 */
@Data
public class TripVehicleRequest {

    private Long vehicleId;

    private String plateNo;

    private String vehicleType;

    private BigDecimal vehicleLoad;

    private BigDecimal vehicleVolume;

    private Long driverId;

    private String driverName;

    private String driverPhone;
}
