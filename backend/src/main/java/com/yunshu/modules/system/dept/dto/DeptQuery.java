package com.yunshu.modules.system.dept.dto;

import com.yunshu.common.query.BaseListQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 机构列表查询参数：筛选与分页均走服务端。
 */
@Data
@Schema(description = "机构列表查询参数")
public class DeptQuery extends BaseListQuery {

    @Schema(description = "数据范围根机构ID，查询其全部后代（不含自身）；默认 1=总部")
    private Long rootId;

    @Schema(description = "机构名称，支持多模式匹配")
    private String deptName;

    @Schema(description = "机构编码，支持多模式匹配")
    private String deptCode;

    @Schema(description = "机构类型：1总部 2公司 3部门 4分拨中心 5网点；支持多选")
    private List<Integer> deptType;

    @Schema(description = "机构性质：1自营 2加盟 3合作；支持多选")
    private List<Integer> deptNature;

    @Schema(description = "直接上级机构ID，精确匹配；支持多选")
    private List<Long> parentId;

    @Schema(description = "负责人，支持多模式匹配")
    private String leader;

    @Schema(description = "联系电话，支持多模式匹配")
    private String phone;

    @Schema(description = "省编码，精确匹配")
    private String provinceCode;

    @Schema(description = "市编码，精确匹配")
    private String cityCode;

    @Schema(description = "区县编码，精确匹配")
    private String districtCode;

    @Schema(description = "末端网点适用分公司ID；仅命中该分公司的末端网点；支持多选")
    private List<Long> applyCompanyId;

    @Schema(description = "创建人姓名，支持多模式匹配（依赖 sys_user；表不存在时忽略）")
    private String createByName;

    @Schema(description = "最后修改人姓名，支持多模式匹配（依赖 sys_user；表不存在时忽略）")
    private String updateByName;

    @Schema(description = "创建时间起，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String createTimeStart;

    @Schema(description = "创建时间止，yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String createTimeEnd;

    @Schema(description = "最后修改时间起")
    private String updateTimeStart;

    @Schema(description = "最后修改时间止")
    private String updateTimeEnd;

    @Schema(description = "排序字段：deptName/parentName/deptType/deptNature/deptCode/leader/phone/isLeaf/status/regionName/applyCompanyNames/createTime/updateTime/createByName/updateByName")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc")
    private String sortOrder;

    @Schema(description = "是否末端：true=是 false=否；支持多选")
    private List<Boolean> isLeaf;

    @Schema(description = "状态：true=启用 false=禁用；支持多选")
    private List<Boolean> status;

    @Schema(description = "页码，从 1 开始")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，默认 20")
    private Integer pageSize = 20;
}
