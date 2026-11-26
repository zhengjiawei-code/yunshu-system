package com.yunshu.modules.transport.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 修改车次请求：只允许车次计划/装车配载状态；目的网点传 null 表示不变。 */
@Data
public class TripUpdateRequest {

    @Size(max = 64, message = "车次名称不能超过64个字符")
    private String tripName;

    private LocalDateTime planDepartTime;

    private Long originDeptId;

    private List<Long> destDeptIds;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
