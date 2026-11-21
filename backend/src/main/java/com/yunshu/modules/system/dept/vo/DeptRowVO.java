package com.yunshu.modules.system.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机构平铺列表行：在机构字段之上补充上级名称与完整路径，供“上级机构”列展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "机构列表行")
public class DeptRowVO extends DeptVO {

    @Schema(description = "直接上级机构名称")
    private String parentName;

    @Schema(description = "完整路径，如 总部 / 华东公司 / 上海分拨中心")
    private String parentPath;
}
