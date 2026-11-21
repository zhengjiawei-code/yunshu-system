package com.yunshu.modules.system.route.vo;

import lombok.Data;

/** 路由保存结果：reused=true 表示同键路由已存在，仅补充授权，公共字段未被覆盖 */
@Data
public class RouteSaveResultVO {

    private Long id;

    private Boolean reused;

    private String message;
}
