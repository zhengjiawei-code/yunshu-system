package com.yunshu.modules.system.dept.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 管理范围树节点：只返回当前账号可见的机构 */
@Data
public class ManageScopeNodeVO {

    private Long id;

    private Long parentId;

    private String deptName;

    private String deptCode;

    /** 1=总部 2=公司 3=部门 4=分拨中心 5=网点 */
    private Integer deptType;

    private Boolean isLeaf;

    private Boolean status;

    private Integer sortOrder;

    private List<ManageScopeNodeVO> children = new ArrayList<>();
}
