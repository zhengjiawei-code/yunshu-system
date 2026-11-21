package com.yunshu.modules.system.role.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色视图对象，字段名与前端 RoleRecord 类型对齐。
 */
@Data
@Schema(description = "角色")
public class RoleVO {

    private Long id;

    private String roleName;

    private String roleCode;

    private String roleDesc;

    private Boolean status;

    private String remark;

    private Long createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Long updateBy;

    /** 最后修改人姓名：由 sys_user 关联解析；未建表时为 null，前端显示“—” */
    private String updateByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
