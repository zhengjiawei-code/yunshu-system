package com.yunshu.modules.operation.dto;

import lombok.Data;

/** 作废请求（方案 6.1）：原因必填，version 为乐观锁令牌（运单 updateTime） */
@Data
public class WaybillVoidRequest {

    private String reason;

    private String version;
}
