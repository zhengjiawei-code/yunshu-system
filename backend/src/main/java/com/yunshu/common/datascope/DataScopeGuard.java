package com.yunshu.common.datascope;

import com.yunshu.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 业务写侧越权校验：编辑/删除/详情必须显式调用，越权直接拒绝，不依赖“更新 0 行”。
 */
@Component
public class DataScopeGuard {

    /** 编辑与删除前的归属校验 */
    public void assertWritable(Long deptId, Long createBy) {
        DataScopeContext context = DataScopeContext.current();
        if (context == null) {
            throw new BizException(403, "数据权限上下文缺失，拒绝操作");
        }
        switch (context.getScope()) {
            case DataScopeContext.SCOPE_ALL -> {
                return;
            }
            case DataScopeContext.SCOPE_DEPT -> {
                if (deptId != null && deptId.equals(context.getDeptId())) {
                    return;
                }
            }
            case DataScopeContext.SCOPE_SUBTREE -> {
                if (context.containsDept(deptId)) {
                    return;
                }
            }
            case DataScopeContext.SCOPE_SELF -> {
                if (createBy != null && createBy.equals(context.getUserId())) {
                    return;
                }
            }
            default -> {
                // 非法档位一律拒绝
            }
        }
        throw new BizException(403, "无权操作其他机构或其他人的数据");
    }

    /** 详情查看的归属校验，口径与列表一致 */
    public void assertReadable(Long deptId, Long createBy) {
        assertWritable(deptId, createBy);
    }
}
