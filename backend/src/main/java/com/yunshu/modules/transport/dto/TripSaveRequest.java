package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 创建/修改车次请求；目的网点至少 1 个且去重。 */
@Data
public class TripSaveRequest {

    @NotBlank(message = "车次名称不能为空")
    @Size(max = 64, message = "车次名称不能超过64个字符")
    private String tripName;

    @NotNull(message = "计划发车时间不能为空")
    private LocalDateTime planDepartTime;

    /** 为空时默认当前机构 */
    private Long originDeptId;

    @NotEmpty(message = "目的网点至少选择1个")
    private List<Long> destDeptIds;

    private Long vehicleId;

    private String plateNo;

    private String vehicleType;

    private BigDecimal vehicleLoad;

    private BigDecimal vehicleVolume;

    private Long driverId;

    private String driverName;

    private String driverPhone;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
