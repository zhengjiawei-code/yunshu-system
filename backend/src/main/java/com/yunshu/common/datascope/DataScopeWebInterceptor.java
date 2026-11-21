package com.yunshu.common.datascope;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.auth.service.CurrentUserResolver;
import com.yunshu.modules.auth.service.PermissionService;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每个请求解析登录态 -> 数据权限上下文；未登录请求不设置上下文，业务表查询由拦截器 fail-closed。
 * 超管（id=1 或 admin 角色）为全部数据；客户账号不适用数据范围，按仅本人（最严格）处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataScopeWebInterceptor implements HandlerInterceptor {

    private final CurrentUserResolver currentUserResolver;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final PermissionService permissionService;

    @Value("${yunshu.data-scope.enabled:false}")
    private boolean enabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 防止线程复用导致上下文串号
        DataScopeContext.clear();
        if (!enabled) {
            return true;
        }
        Long userId = currentUserResolver.id(request);
        if (userId == null) {
            return true;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getStatus())) {
            return true;
        }
        if (permissionService.isSuperAdmin(user)) {
            // 超管不受数据范围限制，但保留主机构用于新增数据时的归属填充
            DataScopeContext.set(DataScopeContext.of(user.getId(), user.getUsername(),
                    user.getDeptId(), DataScopeContext.SCOPE_ALL, Set.of()));
            return true;
        }

        int scope;
        if (Integer.valueOf(2).equals(user.getUserType())) {
            // 客户账号：不适用数据范围，按仅本人处理（客户域规则后续单独接入）
            scope = DataScopeContext.SCOPE_SELF;
        } else {
            scope = user.getDataScope() == null ? DataScopeContext.SCOPE_SUBTREE : user.getDataScope();
            if (scope < 1 || scope == 2 || scope > 5) {
                // 2 为预留未实现，其余为脏数据：按最严格处理，绝不放行
                log.warn("用户 {} 数据范围档位异常（{}），按仅本人处理", user.getUsername(), scope);
                scope = DataScopeContext.SCOPE_SELF;
            }
        }

        Set<Long> subtree = scope == DataScopeContext.SCOPE_SUBTREE
                ? subtreeOf(user.getDeptId())
                : Set.of();
        DataScopeContext.set(DataScopeContext.of(user.getId(), user.getUsername(), user.getDeptId(), scope, subtree));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        DataScopeContext.clear();
    }

    /** 主机构为根的整棵子树（含自身）；机构数据是小表，按请求计算一次 */
    private Set<Long> subtreeOf(Long deptId) {
        if (deptId == null) {
            return Set.of();
        }
        List<SysDept> depts = deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .select(SysDept::getId, SysDept::getParentId));
        Map<Long, List<Long>> children = new HashMap<>();
        depts.forEach(dept -> children
                .computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(), key -> new ArrayList<>())
                .add(dept.getId()));
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(deptId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current == null || !result.add(current)) {
                continue;
            }
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return result;
    }
}
