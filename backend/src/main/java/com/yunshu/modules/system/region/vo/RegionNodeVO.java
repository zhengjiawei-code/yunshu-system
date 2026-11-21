package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 区划下拉节点。
 */
@Data
@Schema(description = "区划节点")
public class RegionNodeVO {

    private String code;

    private String name;

    @Schema(description = "层级：1=省 2=市 3=区县 4=乡级")
    private Integer level;

    @Schema(description = "是否存在下级有效区划")
    private Boolean hasChildren;
}
