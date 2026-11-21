package com.yunshu.common.datascope;

/**
 * 数据权限异常：SQL 结构无法安全处理、上下文缺失等场景直接抛出，宁可报错也不放行。
 */
public class DataScopeException extends RuntimeException {

    public DataScopeException(String message) {
        super(message);
    }

    public DataScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
