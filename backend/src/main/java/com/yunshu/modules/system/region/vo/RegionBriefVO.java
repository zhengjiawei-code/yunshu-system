package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 预检/导入差异中的区划简要信息。
 */
@Data
@Schema(description = "区划简要信息")
public class RegionBriefVO {

    private String code;

    private String name;

    private String fullName;

    private Integer level;
}
