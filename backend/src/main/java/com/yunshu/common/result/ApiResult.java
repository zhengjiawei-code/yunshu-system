package com.yunshu.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一响应体：前端 request.ts 以 code=200 判定成功并直接取 data。
 */
@Data
@Schema(description = "统一响应体")
public class ApiResult<T> {

    @Schema(description = "业务状态码，200=成功")
    private int code;

    @Schema(description = "提示信息，失败时前端直接展示")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static ApiResult<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
