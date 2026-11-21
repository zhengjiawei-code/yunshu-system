package com.yunshu.common.exception;

import com.yunshu.common.datascope.DataScopeException;
import com.yunshu.common.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：HTTP 状态保持 200，业务结果由响应体 code 表达，与前端请求层约定一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResult<Void> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        return ApiResult.fail(400, message);
    }

    @ExceptionHandler(DataScopeException.class)
    public ApiResult<Void> handleDataScope(DataScopeException e) {
        // 数据权限无法安全处理时宁可报错也不放行
        log.error("数据权限拦截：{}", e.getMessage());
        return ApiResult.fail(403, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        // MyBatis 会把拦截器内抛出的数据权限异常包一层，这里解包后仍按拒绝处理
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof DataScopeException dataScopeException) {
                log.error("数据权限拦截：{}", dataScopeException.getMessage());
                return ApiResult.fail(403, dataScopeException.getMessage());
            }
            cause = cause.getCause();
        }
        log.error("系统异常", e);
        return ApiResult.fail(500, "系统异常，请稍后重试");
    }
}
