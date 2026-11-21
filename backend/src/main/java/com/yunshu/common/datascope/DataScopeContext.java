package com.yunshu.common.datascope;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 数据权限上下文：每次请求由 Web 拦截器写入，请求结束清理。
 * 缺上下文时一律 fail-closed（SQL 追加 1=0，写操作直接拒绝）；
 * 系统任务/定时任务必须显式 runAsSystem 表示不受数据范围限制。
 */
public final class DataScopeContext {

    /** 1=全部数据 */
    public static final int SCOPE_ALL = 1;
    /** 3=本机构 */
    public static final int SCOPE_DEPT = 3;
    /** 4=本机构及以下 */
    public static final int SCOPE_SUBTREE = 4;
    /** 5=仅本人 */
    public static final int SCOPE_SELF = 5;

    /** 业务表归属机构列命名约定 */
    public static final String DEPT_COLUMN = "dept_id";
    /** 业务表归属人列命名约定 */
    public static final String OWNER_COLUMN = "create_by";

    private static final ThreadLocal<DataScopeContext> HOLDER = new ThreadLocal<>();

    private final Long userId;
    private final String username;
    private final Long deptId;
    private final Integer scope;
    private final Set<Long> subtreeDeptIds;
    private final boolean system;

    private DataScopeContext(Long userId, String username, Long deptId, Integer scope,
                             Set<Long> subtreeDeptIds, boolean system) {
        this.userId = userId;
        this.username = username;
        this.deptId = deptId;
        this.scope = scope;
        this.subtreeDeptIds = subtreeDeptIds == null ? Set.of() : Set.copyOf(subtreeDeptIds);
        this.system = system;
    }

    public static DataScopeContext of(Long userId, String username, Long deptId,
                                      Integer scope, Set<Long> subtreeDeptIds) {
        return new DataScopeContext(userId, username, deptId, scope, subtreeDeptIds, false);
    }

    /** 系统级上下文：显式声明不受数据范围限制 */
    public static DataScopeContext all() {
        return new DataScopeContext(null, "system", null, SCOPE_ALL, Set.of(), true);
    }

    public static void set(DataScopeContext context) {
        HOLDER.set(context);
    }

    public static DataScopeContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static DataScopeContext require(String action) {
        DataScopeContext context = HOLDER.get();
        if (context == null) {
            throw new DataScopeException("数据权限上下文缺失，拒绝操作：" + action);
        }
        return context;
    }

    /** 异步/定时任务需要访问业务数据时显式声明系统上下文 */
    public static <T> T runAsSystem(Supplier<T> supplier) {
        DataScopeContext previous = HOLDER.get();
        HOLDER.set(all());
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    public static void runAsSystem(Runnable runnable) {
        runAsSystem(() -> {
            runnable.run();
            return null;
        });
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Long getDeptId() {
        return deptId;
    }

    public Integer getScope() {
        return scope;
    }

    public Set<Long> getSubtreeDeptIds() {
        return subtreeDeptIds;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean containsDept(Long targetDeptId) {
        return targetDeptId != null && subtreeDeptIds.contains(targetDeptId);
    }
}
