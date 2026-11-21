package com.yunshu.modules.system.role.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 角色分页结果。
 */
@Data
@Schema(description = "角色分页结果")
public class RolePageVO {

    @Schema(description = "当前页数据")
    private List<RoleVO> records;

    @Schema(description = "筛选后的总条数（用于分页与合计行）")
    private long total;
}
