package com.yunshu.common.exception;

import lombok.Getter;

/**
 * 业务校验失败异常，由全局异常处理器转换为 code=400 的统一响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
