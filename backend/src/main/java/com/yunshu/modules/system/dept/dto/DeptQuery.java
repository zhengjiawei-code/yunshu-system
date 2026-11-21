package com.yunshu.modules.system.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 机构列表查询参数：筛选与分页均走服务端。
 */
@Data
@Schema(description = "机构列表查询参数")
public class DeptQuery {

    @Schema(description = "数据范围根机构ID，查询其全部后代（不含自身）；默认 1=总部")
    private Long rootId;

    @Schema(description = "机构名称，模糊匹配")
    private String deptName;

    @Schema(description = "机构编码，模糊匹配")
    private String deptCode;

    @Schema(description = "机构类型：1总部 2公司 3部门 4分拨中心 5网点")
    private Integer deptType;

    @Schema(description = "机构性质：1自营 2加盟 3合作")
    private Integer deptNature;

    @Schema(description = "直接上级机构ID，精确匹配")
    private Long parentId;

    @Schema(description = "负责人，模糊匹配")
    private String leader;

    @Schema(description = "联系电话，模糊匹配")
    private String phone;

    @Schema(description = "创建人姓名，模糊匹配（依赖 sys_user；表不存在时忽略）")
    private String createByName;

    @Schema(description = "最后修改人姓名，模糊匹配（依赖 sys_user；表不存在时忽略）")
    private String updateByName;

    @Schema(description = "创建时间起，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String createTimeStart;

    @Schema(description = "创建时间止，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String createTimeEnd;

    @Schema(description = "最后修改时间起")
    private String updateTimeStart;

    @Schema(description = "最后修改时间止")
    private String updateTimeEnd;

    @Schema(description = "排序字段：deptName/parentName/deptType/deptNature/deptCode/leader/phone/isLeaf/status/createTime/updateTime/createByName/updateByName")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc")
    private String sortOrder;

    @Schema(description = "是否末端：true=是 false=否")
    private Boolean isLeaf;

    @Schema(description = "状态：true=启用 false=禁用")
    private Boolean status;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认 20")
    private Integer pageSize = 20;
}
