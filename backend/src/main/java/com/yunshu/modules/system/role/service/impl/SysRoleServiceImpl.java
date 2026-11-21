package com.yunshu.modules.system.role.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.common.filter.TextFilterMode;
import com.yunshu.modules.auth.service.PermissionService;
import com.yunshu.modules.system.menu.entity.SysMenu;
import com.yunshu.modules.system.menu.mapper.SysMenuMapper;
import com.yunshu.modules.system.role.dto.RoleQuery;
import com.yunshu.modules.system.role.dto.RoleSaveRequest;
import com.yunshu.modules.system.role.entity.SysRole;
import com.yunshu.modules.system.role.entity.SysRoleMenu;
import com.yunshu.modules.system.role.mapper.SysRoleMapper;
import com.yunshu.modules.system.role.mapper.SysRoleMenuMapper;
import com.yunshu.modules.system.role.service.SysRoleService;
import com.yunshu.modules.system.role.vo.RolePageVO;
import com.yunshu.modules.system.role.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    /** 内置超级管理员角色编码：不可删除/禁用/改权限 */
    private static final String ADMIN_CODE = "admin";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final PermissionService permissionService;

    @Override
    @DS("slave")
    public RolePageVO page(RoleQuery query) {
        List<SysRole> all = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery());

        // sys_user 未建表时：姓名列显示“—”，姓名筛选项整体忽略（不能把所有行过滤掉）
        boolean userTableReady = roleMapper.countTable("sys_user") > 0;
        Map<Long, String> userNames = userTableReady ? resolveUserNames(all) : Map.of();

        String name = trim(query.getRoleName());
        String code = trim(query.getRoleCode());
        String desc = trim(query.getRoleDesc());
        String updateByName = trim(query.getUpdateByName());
        LocalDateTime updateStart = parseStart(query.getUpdateTimeStart());
        LocalDateTime updateEnd = parseEnd(query.getUpdateTimeEnd());
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());

        List<SysRole> filtered = all.stream()
                .filter(item -> TextFilterMode.match(item.getRoleName(), name,
                        TextFilterMode.modeOf(modes, "roleName")))
                .filter(item -> TextFilterMode.match(item.getRoleCode(), code,
                        TextFilterMode.modeOf(modes, "roleCode")))
                .filter(item -> TextFilterMode.match(item.getRoleDesc(), desc,
                        TextFilterMode.modeOf(modes, "roleDesc")))
                .filter(item -> updateByName == null || !userTableReady
                        || TextFilterMode.match(nameOf(userNames, item.getUpdateBy()), updateByName,
                        TextFilterMode.modeOf(modes, "updateByName")))
                .filter(item -> updateStart == null
                        || (item.getUpdateTime() != null && !item.getUpdateTime().isBefore(updateStart)))
                .filter(item -> updateEnd == null
                        || (item.getUpdateTime() != null && !item.getUpdateTime().isAfter(updateEnd)))
                .sorted(buildComparator(query, userNames))
                .toList();

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 10000);
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());

        List<RoleVO> records = filtered.subList(from, to).stream()
                .map(item -> toVO(item, userNames))
                .toList();

        RolePageVO result = new RolePageVO();
        result.setRecords(records);
        result.setTotal(filtered.size());
        return result;
    }

    @Override
    @DS("slave")
    public RoleVO detail(Long id) {
        SysRole role = require(id);
        return toVO(role, resolveUserNames(List.of(role)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long create(RoleSaveRequest request) {
        String name = trim(request.getRoleName());
        String code = trim(request.getRoleCode());
        checkNameUnique(name, null);
        checkCodeUnique(code, null);

        SysRole role = new SysRole();
        role.setRoleName(name);
        role.setRoleCode(code);
        role.setRoleDesc(trim(request.getRoleDesc()));
        role.setStatus(request.getStatus() == null || request.getStatus());
        role.setRemark(trim(request.getRemark()));
        roleMapper.insert(role);
        permissionService.evictAll();
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, RoleSaveRequest request) {
        SysRole existing = require(id);
        boolean admin = isAdmin(existing);

        if (!Objects.equals(trim(request.getRoleCode()), existing.getRoleCode())) {
            throw new BizException("角色编码创建后不可修改");
        }
        if (admin && Boolean.FALSE.equals(request.getStatus())) {
            throw new BizException("超级管理员角色不允许禁用");
        }

        String name = trim(request.getRoleName());
        checkNameUnique(name, id);

        Boolean status = request.getStatus() == null ? existing.getStatus() : request.getStatus();
        // 显式 set：清空角色描述/备注时需要写入 NULL
        roleMapper.update(null, Wrappers.<SysRole>lambdaUpdate()
                .eq(SysRole::getId, id)
                .set(SysRole::getRoleName, name)
                .set(SysRole::getRoleDesc, trim(request.getRoleDesc()))
                .set(SysRole::getStatus, status)
                .set(SysRole::getRemark, trim(request.getRemark())));
        permissionService.evictAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id) {
        SysRole role = require(id);
        if (isAdmin(role)) {
            throw new BizException("超级管理员角色不允许删除");
        }
        // sys_user_role 未建表时跳过用户引用校验，用户模块建表后自动生效
        if (roleMapper.countTable("sys_user_role") > 0 && roleMapper.countUserRoleRef(id) > 0) {
            throw new BizException("该角色已分配给用户，请先解除用户关联后再删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
        permissionService.evictAll();
    }

    @Override
    @DS("slave")
    public List<Long> menuIds(Long roleId) {
        SysRole role = require(roleId);
        if (isAdmin(role)) {
            // admin 运行时直通全量菜单，回显也返回全量，避免后续新增菜单后回显缺项
            return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery().select(SysMenu::getId))
                    .stream()
                    .map(SysMenu::getId)
                    .toList();
        }
        return roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void saveMenus(Long roleId, List<Long> menuIds) {
        SysRole role = require(roleId);
        if (isAdmin(role)) {
            throw new BizException("超级管理员角色不允许修改权限");
        }

        Set<Long> requested = new LinkedHashSet<>();
        if (menuIds != null) {
            menuIds.stream().filter(Objects::nonNull).forEach(requested::add);
        }
        // 只保留真实存在的菜单，避免脏数据写进关联表
        Set<Long> valid = requested.isEmpty() ? Set.of() : new HashSet<>(
                menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                                .select(SysMenu::getId)
                                .in(SysMenu::getId, requested))
                        .stream()
                        .map(SysMenu::getId)
                        .toList());

        // 整体覆盖：先清空再插入
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : requested) {
            if (!valid.contains(menuId)) {
                continue;
            }
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
        permissionService.evictAll();
    }

    @Override
    @DS("slave")
    public List<RoleVO> options() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getStatus, true)
                        .orderByAsc(SysRole::getId))
                .stream()
                .map(role -> toVO(role, Map.of()))
                .toList();
    }

    /* ---------------- 内部实现 ---------------- */

    private SysRole require(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在或已删除");
        }
        return role;
    }

    private boolean isAdmin(SysRole role) {
        return ADMIN_CODE.equalsIgnoreCase(role.getRoleCode());
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleName, name)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new BizException("角色名称已存在：" + name);
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, code)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new BizException("角色编码已存在：" + code);
        }
    }

    private Map<Long, String> resolveUserNames(List<SysRole> roles) {
        Set<Long> ids = new HashSet<>();
        roles.forEach(role -> {
            if (role.getUpdateBy() != null) {
                ids.add(role.getUpdateBy());
            }
            if (role.getCreateBy() != null) {
                ids.add(role.getCreateBy());
            }
        });
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        roleMapper.selectUserNames(ids).forEach(row -> {
            Object id = row.get("id");
            Object realName = row.get("realName");
            if (id != null) {
                names.put(((Number) id).longValue(), realName == null ? null : String.valueOf(realName));
            }
        });
        return names;
    }

    private String nameOf(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * 列表排序：未指定 sortBy 时按 id asc；姓名列走 sys_user 解析值；null 永远排最后。
     */
    private Comparator<SysRole> buildComparator(RoleQuery query, Map<Long, String> userNames) {
        Comparator<SysRole> fallback = Comparator.comparing(SysRole::getId);
        if (!StringUtils.hasText(query.getSortBy())) {
            return fallback;
        }
        Comparator<SysRole> comparator = switch (query.getSortBy()) {
            case "roleName" -> Comparator.comparing(SysRole::getRoleName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "roleCode" -> Comparator.comparing(SysRole::getRoleCode,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "roleDesc" -> Comparator.comparing(SysRole::getRoleDesc,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateByName" -> Comparator.<SysRole, String>comparing(
                    role -> nameOf(userNames, role.getUpdateBy()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SysRole::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateTime" -> Comparator.comparing(SysRole::getUpdateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "id" -> Comparator.comparing(SysRole::getId);
            default -> null;
        };
        if (comparator == null) {
            return fallback;
        }
        if ("desc".equalsIgnoreCase(query.getSortOrder())) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SysRole::getId);
    }

    private RoleVO toVO(SysRole role, Map<Long, String> userNames) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleDesc(role.getRoleDesc());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateBy(role.getCreateBy());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateBy(role.getUpdateBy());
        vo.setUpdateByName(nameOf(userNames, role.getUpdateBy()));
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime parseStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 10
                ? LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
                : LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private LocalDateTime parseEnd(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 10
                ? LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59)
                : LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
