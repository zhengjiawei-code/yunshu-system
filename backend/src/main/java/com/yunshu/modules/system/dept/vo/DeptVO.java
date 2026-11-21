package com.yunshu.modules.system.dept.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 机构树节点，字段名与前端 DeptNode 类型对齐。
 */
@Data
@Schema(description = "机构树节点")
public class DeptVO {

    private Long id;

    private Long parentId;

    private String deptName;

    private String deptCode;

    @Schema(description = "1=总部 2=公司 3=部门 4=分拨中心 5=网点")
    private Integer deptType;

    @Schema(description = "机构性质：1=自营 2=加盟 3=合作；总部固定为自营")
    private Integer deptNature;

    @Schema(description = "是否末端：仅网点可为 true")
    private Boolean isLeaf;

    private Boolean allowShip;

    private Boolean allowArrival;

    private Boolean allowPrepaidUnpaid;

    private Boolean allowCollectUnpaidSign;

    private String leader;

    private String phone;

    private String address;

    private Integer sortOrder;

    private Boolean status;

    private String remark;

    private Long createBy;

    @Schema(description = "创建人姓名：由 sys_user 关联解析；用户表未建时为 null，前端展示 —")
    private String createByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Long updateBy;

    @Schema(description = "最后修改人姓名：由 sys_user 关联解析；用户表未建时为 null")
    private String updateByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private List<DeptVO> children;
}
