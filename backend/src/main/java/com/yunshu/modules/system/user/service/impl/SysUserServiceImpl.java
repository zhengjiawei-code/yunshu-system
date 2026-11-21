package com.yunshu.modules.system.user.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.auth.service.PermissionService;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.menu.entity.SysMenu;
import com.yunshu.modules.system.menu.mapper.SysMenuMapper;
import com.yunshu.modules.system.role.entity.SysRole;
import com.yunshu.modules.system.role.entity.SysRoleMenu;
import com.yunshu.modules.system.role.mapper.SysRoleMapper;
import com.yunshu.modules.system.role.mapper.SysRoleMenuMapper;
import com.yunshu.modules.system.user.dto.UserQuery;
import com.yunshu.modules.system.user.dto.UserSaveRequest;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.entity.SysUserMenu;
import com.yunshu.modules.system.user.entity.SysUserOrg;
import com.yunshu.modules.system.user.entity.SysUserRole;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.system.user.mapper.SysUserMenuMapper;
import com.yunshu.modules.system.user.mapper.SysUserOrgMapper;
import com.yunshu.modules.system.user.mapper.SysUserRoleMapper;
import com.yunshu.modules.system.user.service.SysUserService;
import com.yunshu.modules.system.user.vo.UserPageVO;
import com.yunshu.modules.system.user.vo.UserPermVO;
import com.yunshu.modules.system.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class SysUserServiceImpl implements SysUserService {

    private static final Long SUPER_ADMIN_ID = 1L;
    private static final String ADMIN_ROLE_CODE = "admin";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final int TYPE_INTERNAL = 1;
    private static final List<Integer> ALLOWED_SCOPES = List.of(1, 3, 4, 5);
    private static final int DEFAULT_SCOPE = 4;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final SysUserMenuMapper userMenuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @DS("slave")
    public UserPageVO page(UserQuery query) {
        List<SysUser> all = userMapper.selectList(Wrappers.<SysUser>lambdaQuery());
        Context context = new Context(all);

        String username = trim(query.getUsername());
        String realName = trim(query.getRealName());
        String phone = trim(query.getPhone());
        String email = trim(query.getEmail());
        String updateByName = trim(query.getUpdateByName());
        LocalDateTime start = parseStart(query.getUpdateTimeStart());
        LocalDateTime end = parseEnd(query.getUpdateTimeEnd());

        List<SysUser> filtered = all.stream()
                .filter(user -> username == null || containsIgnoreCase(user.getUsername(), username))
                .filter(user -> realName == null || containsIgnoreCase(user.getRealName(), realName))
                .filter(user -> phone == null || containsIgnoreCase(user.getPhone(), phone))
                .filter(user -> email == null || containsIgnoreCase(user.getEmail(), email))
                .filter(user -> query.getDeptId() == null || Objects.equals(user.getDeptId(), query.getDeptId()))
                .filter(user -> query.getRoleId() == null
                        || context.roleIdsOf(user.getId()).contains(query.getRoleId()))
                .filter(user -> query.getHasDirectPerm() == null
                        || context.hasDirectPerm(user.getId()) == query.getHasDirectPerm())
                .filter(user -> query.getStatus() == null || Objects.equals(user.getStatus(), query.getStatus()))
                .filter(user -> updateByName == null
                        || containsIgnoreCase(context.realNameOf(user.getUpdateBy()), updateByName))
                .filter(user -> start == null
                        || (user.getUpdateTime() != null && !user.getUpdateTime().isBefore(start)))
                .filter(user -> end == null
                        || (user.getUpdateTime() != null && !user.getUpdateTime().isAfter(end)))
                .sorted(buildComparator(query, context))
                .toList();

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 10000);
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());

        List<UserVO> records = filtered.subList(from, to).stream()
                .map(user -> toVO(user, context))
                .toList();

        UserPageVO result = new UserPageVO();
        result.setRecords(records);
        result.setTotal(filtered.size());
        return result;
    }

    @Override
    @DS("slave")
    public UserVO detail(Long id) {
        SysUser user = require(id);
        return toVO(user, new Context(List.of(user)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long create(UserSaveRequest request, Long currentUserId) {
        String username = trim(request.getUsername());
        checkUsernameUnique(username, null);
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BizException("请输入密码");
        }

        int userType = normalizeUserType(request.getUserType());
        validatePhone(request.getPhone());
        validateDept(request.getDeptId(), userType);
        List<Long> roleIds = validateRoles(request.getRoleIds(), userType, currentUserId);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        user.setRealName(request.getRealName().trim());
        user.setPhone(trim(request.getPhone()));
        user.setEmail(trim(request.getEmail()));
        user.setDeptId(request.getDeptId());
        user.setStatus(request.getStatus() == null || request.getStatus());
        user.setUserType(userType);
        user.setDataScope(normalizeDataScope(request.getDataScope(), userType));
        user.setRemark(trim(request.getRemark()));
        user.setCreateBy(currentUserId);
        user.setUpdateBy(currentUserId);
        userMapper.insert(user);

        saveRoles(user.getId(), roleIds, currentUserId);
        saveOrgs(user.getId(), user.getDeptId(), request.getAuthOrgIds(), userType, currentUserId);
        permissionService.evict(user.getId());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, UserSaveRequest request, Long currentUserId) {
        SysUser existing = require(id);
        if (!Objects.equals(trim(request.getUsername()), existing.getUsername())) {
            throw new BizException("用户名创建后不可修改");
        }

        int userType = normalizeUserType(request.getUserType());
        validatePhone(request.getPhone());
        validateDept(request.getDeptId(), userType);
        List<Long> roleIds = validateRoles(request.getRoleIds(), userType, currentUserId);

        boolean superAdminTarget = isSuperAdminUser(existing);
        if (superAdminTarget) {
            if (Boolean.FALSE.equals(request.getStatus())) {
                throw new BizException("超级管理员不允许禁用");
            }
            if (!sameIds(roleIds, existingRoleIds(id))) {
                throw new BizException("超级管理员的角色不允许修改");
            }
        }
        if (id.equals(currentUserId)) {
            if (Boolean.FALSE.equals(request.getStatus())) {
                throw new BizException("不能禁用当前登录账号");
            }
            if (!sameIds(roleIds, existingRoleIds(id))) {
                throw new BizException("不能修改自己的角色");
            }
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setRealName(request.getRealName().trim());
        update.setPhone(trim(request.getPhone()));
        update.setEmail(trim(request.getEmail()));
        update.setDeptId(request.getDeptId());
        update.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        update.setUserType(userType);
        update.setDataScope(normalizeDataScope(request.getDataScope(), userType));
        update.setRemark(trim(request.getRemark()));
        update.setUpdateBy(currentUserId);
        // 显式 set：清空手机号/邮箱/机构/备注时需要写入 NULL
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getRealName, update.getRealName())
                .set(SysUser::getPhone, update.getPhone())
                .set(SysUser::getEmail, update.getEmail())
                .set(SysUser::getDeptId, update.getDeptId())
                .set(SysUser::getStatus, update.getStatus())
                .set(SysUser::getUserType, update.getUserType())
                .set(SysUser::getDataScope, update.getDataScope())
                .set(SysUser::getRemark, update.getRemark())
                .set(SysUser::getUpdateBy, update.getUpdateBy()));

        saveRoles(id, roleIds, currentUserId);
        saveOrgs(id, update.getDeptId(), request.getAuthOrgIds(), userType, currentUserId);
        permissionService.evict(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id, Long currentUserId) {
        SysUser user = require(id);
        if (isSuperAdminUser(user)) {
            throw new BizException("超级管理员不允许删除");
        }
        if (id.equals(currentUserId)) {
            throw new BizException("不能删除当前登录账号");
        }
        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        userOrgMapper.delete(Wrappers.<SysUserOrg>lambdaQuery().eq(SysUserOrg::getUserId, id));
        userMenuMapper.delete(Wrappers.<SysUserMenu>lambdaQuery().eq(SysUserMenu::getUserId, id));
        permissionService.evict(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void changeStatus(Long id, Boolean status, Long currentUserId) {
        SysUser user = require(id);
        if (Boolean.FALSE.equals(status)) {
            if (isSuperAdminUser(user)) {
                throw new BizException("超级管理员不允许禁用");
            }
            if (id.equals(currentUserId)) {
                throw new BizException("不能禁用当前登录账号");
            }
        }
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status)
                .set(SysUser::getUpdateBy, currentUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void resetPassword(Long id, Long currentUserId) {
        require(id);
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, passwordEncoder.encode(DEFAULT_PASSWORD))
                .set(SysUser::getUpdateBy, currentUserId));
    }

    @Override
    @DS("slave")
    public UserPermVO perms(Long id) {
        SysUser user = require(id);
        UserPermVO vo = new UserPermVO();
        if (isSuperAdminUser(user)) {
            vo.setRoleMenuIds(menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                            .select(SysMenu::getId))
                    .stream()
                    .map(SysMenu::getId)
                    .toList());
            vo.setGrantedIds(List.of());
            vo.setDeniedIds(List.of());
            return vo;
        }

        List<Long> roleIds = existingRoleIds(id);
        List<Long> roleMenuIds = roleIds.isEmpty() ? List.of()
                : roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                                .in(SysRoleMenu::getRoleId, roleIds))
                        .stream()
                        .map(SysRoleMenu::getMenuId)
                        .distinct()
                        .toList();
        List<SysUserMenu> direct = userMenuMapper.selectList(Wrappers.<SysUserMenu>lambdaQuery()
                .eq(SysUserMenu::getUserId, id));

        vo.setRoleMenuIds(roleMenuIds);
        vo.setGrantedIds(direct.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getGrantType()))
                .map(SysUserMenu::getMenuId)
                .toList());
        vo.setDeniedIds(direct.stream()
                .filter(item -> Integer.valueOf(0).equals(item.getGrantType()))
                .map(SysUserMenu::getMenuId)
                .toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void savePerms(Long id, List<Long> grantedIds, List<Long> deniedIds, Long currentUserId) {
        SysUser user = require(id);
        if (isSuperAdminUser(user)) {
            throw new BizException("超级管理员拥有全部权限，无需配置");
        }
        if (id.equals(currentUserId)) {
            throw new BizException("不能配置自己的权限");
        }

        // 只保留存在、未删除且启用的菜单，过滤脏 ID
        Set<Long> requested = new LinkedHashSet<>();
        if (grantedIds != null) {
            grantedIds.stream().filter(Objects::nonNull).forEach(requested::add);
        }
        if (deniedIds != null) {
            deniedIds.stream().filter(Objects::nonNull).forEach(requested::add);
        }
        Set<Long> valid = requested.isEmpty() ? Set.of() : menuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .select(SysMenu::getId)
                                .in(SysMenu::getId, requested)
                                .eq(SysMenu::getStatus, true))
                .stream()
                .map(SysMenu::getId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);

        Set<Long> granted = new LinkedHashSet<>();
        if (grantedIds != null) {
            grantedIds.stream().filter(valid::contains).forEach(granted::add);
        }
        Set<Long> denied = new LinkedHashSet<>();
        if (deniedIds != null) {
            deniedIds.stream().filter(valid::contains).forEach(denied::add);
        }
        // 禁止优先：同一节点既授权又禁止时以禁止为准
        granted.removeAll(denied);

        userMenuMapper.delete(Wrappers.<SysUserMenu>lambdaQuery().eq(SysUserMenu::getUserId, id));
        for (Long menuId : granted) {
            insertDirect(id, menuId, 1, currentUserId);
        }
        for (Long menuId : denied) {
            insertDirect(id, menuId, 0, currentUserId);
        }
        permissionService.evict(id);
    }

    /* ---------------- 内部实现 ---------------- */

    private void insertDirect(Long userId, Long menuId, int grantType, Long currentUserId) {
        SysUserMenu relation = new SysUserMenu();
        relation.setUserId(userId);
        relation.setMenuId(menuId);
        relation.setGrantType(grantType);
        relation.setCreateBy(currentUserId);
        userMenuMapper.insert(relation);
    }

    private SysUser require(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在或已删除");
        }
        return user;
    }

    private boolean isSuperAdminUser(SysUser user) {
        if (user == null) {
            return false;
        }
        if (SUPER_ADMIN_ID.equals(user.getId())) {
            return true;
        }
        return existingRoles(user.getId()).stream()
                .anyMatch(role -> ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()));
    }

    private List<SysRole> existingRoles(Long userId) {
        List<Long> roleIds = existingRoleIds(userId);
        return roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds);
    }

    private List<Long> existingRoleIds(Long userId) {
        return userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        if (!StringUtils.hasText(username)) {
            throw new BizException("请输入用户名");
        }
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count > 0) {
            throw new BizException("用户名已存在：" + username);
        }
    }

    private int normalizeUserType(Integer userType) {
        if (userType == null) {
            return TYPE_INTERNAL;
        }
        if (userType != 1 && userType != 2) {
            throw new BizException("用户类型只能是 1（内部员工）或 2（客户）");
        }
        return userType;
    }

    private void validatePhone(String phone) {
        String value = trim(phone);
        if (!StringUtils.hasText(value)) {
            throw new BizException("请输入手机号");
        }
        if (!value.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("手机号格式不正确");
        }
    }

    /** 内部员工必须有可用机构；客户允许为空，但填了就必须真实存在且启用 */
    private void validateDept(Long deptId, int userType) {
        if (deptId == null) {
            if (userType == TYPE_INTERNAL) {
                throw new BizException("内部员工必须选择所属机构");
            }
            return;
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException("所属机构不存在或已删除");
        }
        if (!Boolean.TRUE.equals(dept.getStatus())) {
            throw new BizException("所属机构已停用：" + dept.getDeptName());
        }
    }

    private List<Long> validateRoles(List<Long> roleIds, int userType, Long currentUserId) {
        Set<Long> requested = new LinkedHashSet<>();
        if (roleIds != null) {
            roleIds.stream().filter(Objects::nonNull).forEach(requested::add);
        }
        if (requested.isEmpty()) {
            throw new BizException("请至少分配 1 个角色");
        }
        List<SysRole> roles = roleMapper.selectBatchIds(requested);
        if (roles.size() != requested.size()) {
            throw new BizException("存在无效的角色，请刷新后重试");
        }
        for (SysRole role : roles) {
            if (!Boolean.TRUE.equals(role.getStatus())) {
                throw new BizException("角色已停用，不能分配：" + role.getRoleName());
            }
            // 防提权：只有超级管理员可以分配 admin 角色
            if (ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode())
                    && currentUserId != null && !permissionService.isSuperAdmin(currentUserId)) {
                throw new BizException("仅超级管理员可以分配超级管理员角色");
            }
        }
        return List.copyOf(requested);
    }

    private Integer normalizeDataScope(Integer dataScope, int userType) {
        if (userType != TYPE_INTERNAL) {
            // 客户不适用数据范围，固定存默认值，仅作存档
            return DEFAULT_SCOPE;
        }
        if (dataScope == null) {
            return DEFAULT_SCOPE;
        }
        if (!ALLOWED_SCOPES.contains(dataScope)) {
            throw new BizException("数据范围只能是 1 全部、3 本机构、4 本机构及以下、5 仅本人");
        }
        return dataScope;
    }

    private void saveRoles(Long userId, List<Long> roleIds, Long currentUserId) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setCreateBy(currentUserId);
            userRoleMapper.insert(relation);
        }
    }

    /** 只存额外机构：主机构由 dept_id 表达，客户不参与可登录机构 */
    private void saveOrgs(Long userId, Long deptId, List<Long> authOrgIds, int userType, Long currentUserId) {
        userOrgMapper.delete(Wrappers.<SysUserOrg>lambdaQuery().eq(SysUserOrg::getUserId, userId));
        if (userType != TYPE_INTERNAL || authOrgIds == null || authOrgIds.isEmpty()) {
            return;
        }
        Set<Long> extras = new LinkedHashSet<>();
        authOrgIds.stream()
                .filter(Objects::nonNull)
                .filter(orgId -> !Objects.equals(orgId, deptId))
                .forEach(extras::add);
        if (extras.isEmpty()) {
            return;
        }
        List<SysDept> depts = deptMapper.selectBatchIds(extras);
        if (depts.size() != extras.size()) {
            throw new BizException("存在无效的可登录机构，请刷新后重试");
        }
        for (SysDept dept : depts) {
            if (!Boolean.TRUE.equals(dept.getStatus())) {
                throw new BizException("可登录机构已停用：" + dept.getDeptName());
            }
            SysUserOrg relation = new SysUserOrg();
            relation.setUserId(userId);
            relation.setOrgId(dept.getId());
            relation.setCreateBy(currentUserId);
            userOrgMapper.insert(relation);
        }
    }

    private boolean sameIds(List<Long> left, List<Long> right) {
        return new HashSet<>(left).equals(new HashSet<>(right));
    }

    /* ---------------- 列表组装 ---------------- */

    /** 一次性载入列表所需的机构、角色、机构授权与直授计数，避免 N+1 */
    private class Context {

        private final Map<Long, String> deptNames = new HashMap<>();
        private final Map<Long, SysRole> roleById = new HashMap<>();
        private final Map<Long, List<Long>> roleIdsByUser = new HashMap<>();
        private final Map<Long, List<Long>> orgIdsByUser = new HashMap<>();
        private final Map<Long, Integer> grantedByUser = new HashMap<>();
        private final Map<Long, Integer> deniedByUser = new HashMap<>();
        private final Map<Long, String> realNames = new HashMap<>();

        Context(List<SysUser> users) {
            deptMapper.selectList(Wrappers.<SysDept>lambdaQuery())
                    .forEach(dept -> deptNames.put(dept.getId(), dept.getDeptName()));
            roleMapper.selectList(Wrappers.<SysRole>lambdaQuery())
                    .forEach(role -> roleById.put(role.getId(), role));
            users.forEach(user -> realNames.put(user.getId(), user.getRealName()));

            userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery())
                    .forEach(relation -> roleIdsByUser
                            .computeIfAbsent(relation.getUserId(), key -> new ArrayList<>())
                            .add(relation.getRoleId()));
            userOrgMapper.selectList(Wrappers.<SysUserOrg>lambdaQuery())
                    .forEach(relation -> orgIdsByUser
                            .computeIfAbsent(relation.getUserId(), key -> new ArrayList<>())
                            .add(relation.getOrgId()));
            userMenuMapper.selectList(Wrappers.<SysUserMenu>lambdaQuery()).forEach(relation -> {
                Map<Long, Integer> target = Integer.valueOf(1).equals(relation.getGrantType())
                        ? grantedByUser : deniedByUser;
                target.merge(relation.getUserId(), 1, Integer::sum);
            });
        }

        List<Long> roleIdsOf(Long userId) {
            return roleIdsByUser.getOrDefault(userId, List.of());
        }

        boolean hasDirectPerm(Long userId) {
            return grantedByUser.getOrDefault(userId, 0) > 0
                    || deniedByUser.getOrDefault(userId, 0) > 0;
        }

        String realNameOf(Long userId) {
            return userId == null ? null : realNames.get(userId);
        }
    }

    private UserVO toVO(SysUser user, Context context) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptId() == null ? null : context.deptNames.get(user.getDeptId()));
        vo.setStatus(user.getStatus());
        vo.setUserType(user.getUserType());
        vo.setDataScope(user.getDataScope());
        vo.setRemark(user.getRemark());
        vo.setCreateBy(user.getCreateBy());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateBy(user.getUpdateBy());
        vo.setUpdateByName(context.realNameOf(user.getUpdateBy()));
        vo.setUpdateTime(user.getUpdateTime());

        // 可登录机构：主机构在前 + 额外机构；客户不参与
        List<Long> authOrgIds = new ArrayList<>();
        if (Integer.valueOf(TYPE_INTERNAL).equals(user.getUserType())) {
            if (user.getDeptId() != null) {
                authOrgIds.add(user.getDeptId());
            }
            for (Long orgId : context.orgIdsByUser.getOrDefault(user.getId(), List.of())) {
                if (!authOrgIds.contains(orgId)) {
                    authOrgIds.add(orgId);
                }
            }
        }
        vo.setAuthOrgIds(authOrgIds);
        vo.setAuthOrgNames(authOrgIds.stream()
                .map(context.deptNames::get)
                .filter(Objects::nonNull)
                .toList());

        List<Long> roleIds = context.roleIdsOf(user.getId()).stream()
                .filter(context.roleById::containsKey)
                .toList();
        vo.setRoleIds(roleIds);
        vo.setRoleNames(roleIds.stream()
                .map(context.roleById::get)
                .map(SysRole::getRoleName)
                .toList());
        vo.setGrantedCount(context.grantedByUser.getOrDefault(user.getId(), 0));
        vo.setDeniedCount(context.deniedByUser.getOrDefault(user.getId(), 0));
        return vo;
    }

    /**
     * 列表排序：未指定 sortBy 时按 id asc；null 永远排最后。
     */
    private Comparator<SysUser> buildComparator(UserQuery query, Context context) {
        Comparator<SysUser> fallback = Comparator.comparing(SysUser::getId);
        if (!StringUtils.hasText(query.getSortBy())) {
            return fallback;
        }
        Comparator<SysUser> comparator = switch (query.getSortBy()) {
            case "username" -> Comparator.comparing(SysUser::getUsername,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "realName" -> Comparator.comparing(SysUser::getRealName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "phone" -> Comparator.comparing(SysUser::getPhone,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "email" -> Comparator.comparing(SysUser::getEmail,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "deptName" -> Comparator.<SysUser, String>comparing(
                    user -> user.getDeptId() == null ? null : context.deptNames.get(user.getDeptId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SysUser::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateByName" -> Comparator.<SysUser, String>comparing(
                    user -> context.realNameOf(user.getUpdateBy()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateTime" -> Comparator.comparing(SysUser::getUpdateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "id" -> Comparator.comparing(SysUser::getId);
            default -> null;
        };
        if (comparator == null) {
            return fallback;
        }
        if ("desc".equalsIgnoreCase(query.getSortOrder())) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SysUser::getId);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
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
