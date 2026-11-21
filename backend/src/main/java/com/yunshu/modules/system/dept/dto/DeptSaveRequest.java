package com.yunshu.modules.system.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 机构新增/编辑载荷，字段与前端 DeptSavePayload 一一对应；
 * 授权 Tab 的四个开关与基础信息共用同一次保存。
 */
@Data
@Schema(description = "机构保存请求")
public class DeptSaveRequest {

    @NotNull(message = "请选择上级机构")
    @Schema(description = "上级机构ID；0=顶级，仅内置总部使用，新增/移动其它机构时必须大于 0")
    private Long parentId;

    @NotNull(message = "请选择机构类型")
    @Min(value = 1, message = "机构类型只能是 1总部 2公司 3部门 4分拨中心 5网点")
    @Max(value = 5, message = "机构类型只能是 1总部 2公司 3部门 4分拨中心 5网点")
    @Schema(description = "机构类型：1=总部 2=公司 3=部门 4=分拨中心 5=网点；总部仅内置，不允许新增")
    private Integer deptType;

    @Schema(description = "是否末端：仅网点可标记；其它类型必须为 false（后端自动清空）")
    private Boolean isLeaf;

    @NotNull(message = "请选择机构性质")
    @Min(value = 1, message = "机构性质只能是 1自营 2加盟 3合作")
    @Max(value = 3, message = "机构性质只能是 1自营 2加盟 3合作")
    @Schema(description = "机构性质：1=自营 2=加盟 3=合作；内置总部固定为自营，不可修改")
    private Integer deptNature;

    @NotBlank(message = "请输入机构名称")
    @Size(min = 2, max = 50, message = "机构名称为 2-50 个字")
    @Schema(description = "机构名称，同一上级下不可重名")
    private String deptName;

    @NotBlank(message = "请输入机构编码")
    @Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "机构编码为 2-20 位字母、数字或下划线")
    @Schema(description = "机构编码，全局唯一；创建后不可修改")
    private String deptCode;

    @Schema(description = "能否发货；为空时按默认值处理")
    private Boolean allowShip;

    @Schema(description = "能否到货；为空时按默认值处理")
    private Boolean allowArrival;

    @Schema(description = "现付未付开单；为空时默认关闭")
    private Boolean allowPrepaidUnpaid;

    @Schema(description = "到付未付签收；为空时默认关闭")
    private Boolean allowCollectUnpaidSign;

    @Size(max = 20, message = "负责人不能超过 20 个字符")
    @Schema(description = "负责人")
    private String leader;

    @Pattern(regexp = "^$|^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})$", message = "请输入正确的手机号或座机号")
    @Schema(description = "联系电话，手机或座机")
    private String phone;

    @Size(max = 200, message = "机构地址不能超过 200 个字符")
    @Schema(description = "机构地址")
    private String address;

    @NotNull(message = "请输入排序号")
    @Min(value = 0, message = "排序号范围为 0-9999")
    @Max(value = 9999, message = "排序号范围为 0-9999")
    @Schema(description = "同级排序号，升序，建议按 10 递增")
    private Integer sortOrder;

    @Schema(description = "状态：true=启用 false=禁用；总部不可禁用")
    private Boolean status;

    @Size(max = 500, message = "备注不能超过 500 字")
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "同级排序用，保存接口忽略")
    private List<Long> ids;
}
