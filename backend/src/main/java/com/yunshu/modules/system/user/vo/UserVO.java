package com.yunshu.modules.system.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象，字段名与前端 UserRecord 类型对齐。
 */
@Data
@Schema(description = "用户")
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String email;

    private Long deptId;

    private String deptName;

    @Schema(description = "主机构 + 额外可登录机构名称，按展示顺序")
    private List<String> authOrgNames;

    @Schema(description = "可登录机构ID列表（主机构在前）")
    private List<Long> authOrgIds;

    private List<Long> roleIds;

    private List<String> roleNames;

    @Schema(description = "额外授权条数（仅统计直接行）")
    private Integer grantedCount;

    @Schema(description = "显式禁止条数（仅统计直接行）")
    private Integer deniedCount;

    private Boolean status;

    @Schema(description = "1=内部员工 2=客户")
    private Integer userType;

    @Schema(description = "1/3/4/5；客户不适用")
    private Integer dataScope;

    private String remark;

    private Long createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Long updateBy;

    private String updateByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
