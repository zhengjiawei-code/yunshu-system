package com.yunshu.modules.system.dept.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 末端网点适用分公司关系，对应 sys_dept_apply_company。
 * 该表已豁免数据权限，create_by 由服务层显式写入。
 */
@Data
@TableName("sys_dept_apply_company")
public class SysDeptApplyCompany {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 末端网点ID（sys_dept.is_leaf=1） */
    private Long leafDeptId;

    /** 适用分公司ID（sys_dept.dept_type=2） */
    private Long companyDeptId;

    private Long createBy;

    private LocalDateTime createTime;
}
