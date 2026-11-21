package com.yunshu.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.system.menu.entity.SysMenu;
import com.yunshu.modules.system.menu.mapper.SysMenuMapper;
import com.yunshu.modules.system.role.entity.SysRole;
import com.yunshu.modules.system.role.entity.SysRoleMenu;
import com.yunshu.modules.system.role.mapper.SysRoleMapper;
import com.yunshu.modules.system.role.mapper.SysRoleMenuMapper;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.entity.SysUserMenu;
import com.yunshu.modules.system.user.entity.SysUserRole;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.system.user.mapper.SysUserMenuMapper;
import com.yunshu.modules.system.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户有效菜单计算：角色菜单 ∪ 账号额外授权 − 显式禁止（级联子孙）。
 * 结果按 userId 缓存，用户/角色/菜单变更时显式失效，避免脏读。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String ADMIN_ROLE_CODE = "admin";
    private static final long SUPER_ADMIN_ID = 1L;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMenuMapper userMenuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;

    private final Map<Long, Set<Long>> cache = new ConcurrentHashMap<>();

    public Set<Long> menuIds(Long userId) {
        return cache.computeIfAbsent(userId, this::compute);
    }

    public boolean isSuperAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        if (userId == SUPER_ADMIN_ID) {
            return true;
        }
        SysUser user = userMapper.selectById(userId);
        return isSuperAdmin(user);
    }

    public boolean isSuperAdmin(SysUser user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        if (user.getId() == SUPER_ADMIN_ID) {
            return true;
        }
        return rolesOf(user.getId()).stream()
                .anyMatch(role -> Boolean.TRUE.equals(role.getStatus())
                        && ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()));
    }

    /** 已启用的角色编码列表 */
    public List<String> roleCodes(Long userId) {
        return rolesOf(userId).stream()
                .filter(role -> Boolean.TRUE.equals(role.getStatus()))
                .map(SysRole::getRoleCode)
                .distinct()
                .toList();
    }

    public List<SysRole> rolesOf(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds);
    }

    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void evictAll() {
        cache.clear();
    }

    private Set<Long> compute(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Set.of();
        }
        List<SysRole> roles = rolesOf(userId);
        List<SysMenu> allMenus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery());
        if (isSuperAdmin(user)) {
            return allMenus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        }

        Set<Long> result = new HashSet<>();
        Set<Long> enabledRoleIds = roles.stream()
                .filter(role -> Boolean.TRUE.equals(role.getStatus()))
                .map(SysRole::getId)
                .collect(Collectors.toSet());
        if (!enabledRoleIds.isEmpty()) {
            roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                            .in(SysRoleMenu::getRoleId, enabledRoleIds))
                    .stream()
                    .map(SysRoleMenu::getMenuId)
                    .forEach(result::add);
        }

        List<SysUserMenu> direct = userMenuMapper.selectList(Wrappers.<SysUserMenu>lambdaQuery()
                .eq(SysUserMenu::getUserId, userId));
        direct.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getGrantType()))
                .map(SysUserMenu::getMenuId)
                .forEach(result::add);

        Set<Long> deniedRoots = direct.stream()
                .filter(item -> Integer.valueOf(0).equals(item.getGrantType()))
                .map(SysUserMenu::getMenuId)
                .collect(Collectors.toSet());
        if (!deniedRoots.isEmpty()) {
            Map<Long, List<Long>> children = new HashMap<>();
            for (SysMenu menu : allMenus) {
                Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
                children.computeIfAbsent(parentId, key -> new java.util.ArrayList<>()).add(menu.getId());
            }
            Set<Long> denied = new HashSet<>();
            Deque<Long> queue = new ArrayDeque<>(deniedRoots);
            while (!queue.isEmpty()) {
                Long current = queue.poll();
                if (current == null || !denied.add(current)) {
                    continue;
                }
                queue.addAll(children.getOrDefault(current, List.of()));
            }
            result.removeAll(denied);
        }
        return result;
    }
}
