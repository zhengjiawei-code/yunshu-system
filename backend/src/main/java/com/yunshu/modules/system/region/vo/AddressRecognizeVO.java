package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 地址识别结果：
 * matched=true 表示识别到至少一级区划；partial=true 表示省/市/区县未全部识别。
 * ambiguous=true 表示存在同名区县且无法用上级消歧，前端应提示手动选择。
 */
@Data
@Schema(description = "地址识别结果")
public class AddressRecognizeVO {

    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;

    /** 是否识别到至少一级 */
    private Boolean matched;
    /** 是否部分识别（省/市/区县不完整） */
    private Boolean partial;
    /** 是否存在同名歧义 */
    private Boolean ambiguous;

    @Schema(description = "歧义候选（全路径），最多 5 条")
    private List<String> candidates;

    @Schema(description = "给用户的提示语")
    private String message;
}
