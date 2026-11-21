package com.yunshu.modules.system.region.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入预检结果：不落库，只返回差异统计与样本。
 */
@Data
@Schema(description = "区划导入预检结果")
public class RegionImportCheckVO {

    private Integer total;

    private Integer added;

    private Integer changed;

    private Integer unchanged;

    @Schema(description = "库中存在、文件缺失的条数（只报告，不自动停用）")
    private Integer disabled;

    @Schema(description = "文件内校验失败条数")
    private Integer invalid;

    private List<String> errors = new ArrayList<>();

    private List<RegionBriefVO> addedList = new ArrayList<>();

    private List<RegionBriefVO> changedList = new ArrayList<>();

    private List<RegionBriefVO> disabledList = new ArrayList<>();

    @Schema(description = "列表仅展示前 200 条")
    private Boolean truncated;
}
