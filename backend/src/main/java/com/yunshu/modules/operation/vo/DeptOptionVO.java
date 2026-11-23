package com.yunshu.modules.operation.vo;

import lombok.Data;

/** 可选开单网点：仅当前账号管理范围内的启用分拨中心/网点且允许发货 */
@Data
public class DeptOptionVO {

    private Long id;

    private String deptName;

    private String deptCode;

    private Integer deptType;
}
