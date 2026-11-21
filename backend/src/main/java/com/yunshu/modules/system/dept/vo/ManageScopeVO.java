package com.yunshu.modules.system.dept.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理范围：总部=全部；分公司=本公司子树 ∪ 适用本公司的末端网点。
 * scopeType=NONE 表示机构链异常，按 fail-closed 返回空。
 */
@Data
public class ManageScopeVO {

    private String scopeType;

    private Long companyId;

    private String companyName;

    private List<ManageScopeNodeVO> tree = new ArrayList<>();

    private List<ManageScopeNodeVO> sharedLeaves = new ArrayList<>();

    private Integer total;
}
