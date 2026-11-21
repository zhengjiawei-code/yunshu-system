package com.yunshu.modules.system.dept.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 机构分页结果。
 */
@Data
@Schema(description = "机构分页结果")
public class DeptPageVO {

    @Schema(description = "当前页数据")
    private List<DeptRowVO> records;

    @Schema(description = "筛选后的总条数（用于分页与合计行）")
    private long total;
}
