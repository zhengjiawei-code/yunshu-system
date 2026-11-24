package com.yunshu.modules.operation.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 改单请求（方案 8.7）。
 * 继承开单请求以复用字段定义与金额计算器；目的地、线路、网点、状态、单号等不可改字段即使传入也被服务端忽略。
 * version 为乐观锁令牌：当前表无 version 列，取详情返回的运单 updateTime（yyyy-MM-dd HH:mm:ss）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WaybillAmendRequest extends WaybillSaveRequest {

    /** 改单原因：必填，≤200 字 */
    private String reason;

    /** 乐观锁令牌：运单最后更新时间（详情/列表返回的 updateTime） */
    private String version;
}
