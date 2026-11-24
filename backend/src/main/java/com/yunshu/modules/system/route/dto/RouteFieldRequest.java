package com.yunshu.modules.system.route.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 短驳/干线单字段增量更新：一次只提交当前修改的那个字段，避免整行覆盖。
 * value 传空字符串/空白表示清空该字段（落库 NULL）。
 */
@Data
public class RouteFieldRequest {

    @NotBlank(message = "请指定要修改的字段")
    private String field;

    private String value;
}
