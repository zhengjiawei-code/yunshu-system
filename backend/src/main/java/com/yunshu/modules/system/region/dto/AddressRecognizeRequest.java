package com.yunshu.modules.system.region.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 地址识别请求：仅用本地已导入区划数据，不调用第三方服务。
 */
@Data
@Schema(description = "地址识别请求")
public class AddressRecognizeRequest {

    @NotBlank(message = "请输入详细地址")
    @Size(max = 200, message = "地址长度不能超过 200 个字符")
    @Schema(description = "详细地址文本")
    private String text;
}
