package com.yunshu.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.menu.service.SysMenuService;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.entity.SysUserOrg;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.system.user.mapper.SysUserOrgMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 真实登录与权限下发：BCrypt 校验 + 服务端 token；菜单权限按角色与账号直授实时计算。
 * 当前机构：会话保存，默认主机构；只允许在“主机构 ∪ sys_user_org”内切换。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final SysDeptMapper deptMapper;
    private final SysMenuService menuService;
    private final PermissionService permissionService;
    private final TokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> login(String username, String password) {
        String account = StringUtils.hasText(username) ? username.trim() : null;
        if (account == null || !StringUtils.hasText(password)) {
            throw new BizException("请输入账号和密码");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, account)
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getStatus())) {
            throw new BizException("账号已被禁用，请联系管理员");
        }
        if (Integer.valueOf(1).equals(user.getUserType())) {
            checkDeptChain(user.getDeptId());
        }

        String token = tokenStore.issue(user.getId());
        List<SysDept> orgs = loginableDepts(user);
        Long currentOrgId = orgs.isEmpty() ? user.getDeptId() : orgs.get(0).getId();
        if (currentOrgId != null) {
            tokenStore.setCurrentOrgId(token, currentOrgId);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("currentOrgId", currentOrgId);
        data.put("currentOrgName", orgName(orgs, currentOrgId));
        data.put("authOrgs", orgMaps(orgs));
        return data;
    }

    public Map<String, Object> info(Long userId, String token) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }

        boolean superAdmin = permissionService.isSuperAdmin(user);
        List<String> roles = permissionService.roleCodes(userId);

        List<SysDept> orgs = loginableDepts(user);
        Long currentOrgId = resolveAnchor(user, tokenStore.currentOrgId(token), orgs);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("userType", user.getUserType());
        userInfo.put("deptId", user.getDeptId());

        SysDept dept = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        String deptName = dept == null ? null : dept.getDeptName();
        userInfo.put("deptName", deptName);
        // 兼容前端既有字段名
        userInfo.put("orgId", user.getDeptId());
        userInfo.put("orgName", deptName);

        userInfo.put("currentOrgId", currentOrgId);
        userInfo.put("currentOrgName", orgName(orgs, currentOrgId));
        userInfo.put("authOrgs", orgMaps(orgs));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userInfo);
        data.put("roles", roles);
        if (superAdmin) {
            data.put("perms", menuService.buttonPerms());
            data.put("menus", menuService.routeTree());
        } else {
            Set<Long> allowed = permissionService.menuIds(userId);
            data.put("perms", menuService.buttonPerms(allowed));
            data.put("menus", menuService.routeTree(allowed));
        }
        return data;
    }

    /** GET /auth/current-org：当前机构 + 可切换列表 */
    public Map<String, Object> currentOrg(Long userId, String token) {
        SysUser user = requireUser(userId);
        List<SysDept> orgs = loginableDepts(user);
        Long currentOrgId = resolveAnchor(user, tokenStore.currentOrgId(token), orgs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentOrgId", currentOrgId);
        data.put("currentOrgName", orgName(orgs, currentOrgId));
        data.put("orgs", orgMaps(orgs));
        return data;
    }

    /** PUT /auth/current-org：越界直接拒绝；不得传任意机构 id */
    public Map<String, Object> switchOrg(Long userId, String token, Long orgId) {
        SysUser user = requireUser(userId);
        if (orgId == null) {
            throw new BizException("请选择要切换的机构");
        }
        List<SysDept> orgs = loginableDepts(user);
        boolean allowed = orgs.stream().anyMatch(item -> Objects.equals(item.getId(), orgId));
        if (!allowed) {
            SysDept target = deptMapper.selectById(orgId);
            if (target == null) {
                throw new BizException(403, "目标机构不存在或已删除，无法切换");
            }
            if (!Boolean.TRUE.equals(target.getStatus())) {
                throw new BizException(403, "目标机构已停用，无法切换");
            }
            throw new BizException(403, "无权限切换到该机构：仅可切换到本人主机构或已授权的可登录机构");
        }
        tokenStore.setCurrentOrgId(token, orgId);
        return currentOrg(userId, token);
    }

    public Map<String, Object> profile(Long userId) {
        SysUser user = requireUser(userId);
        SysDept dept = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("deptId", user.getDeptId());
        data.put("deptName", dept == null ? null : dept.getDeptName());
        data.put("userType", user.getUserType());
        data.put("roles", permissionService.roleCodes(userId));
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, String realName, String phone, String email) {
        requireUser(userId);
        // 用显式 set 写入，允许把选填的手机/邮箱清空（updateById 会忽略 null 字段）
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getRealName, realName == null ? null : realName.trim())
                .set(SysUser::getPhone, StringUtils.hasText(phone) ? phone.trim() : null)
                .set(SysUser::getEmail, StringUtils.hasText(email) ? email.trim() : null)
                .set(SysUser::getUpdateBy, userId));
    }

    /** 修改密码：校验原密码；成功后注销该用户全部会话，强制重新登录 */
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        SysUser user = requireUser(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (!Objects.equals(newPassword, confirmPassword)) {
            throw new BizException("两次输入的新密码不一致");
        }
        if (Objects.equals(oldPassword, newPassword)) {
            throw new BizException("新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setUpdateBy(userId);
        userMapper.updateById(update);
        tokenStore.revokeByUser(userId);
    }

    /** 可登录机构：主机构 + sys_user_org，仅内部员工；已停用/已删除的机构不返回 */
    public List<SysDept> loginableDepts(SysUser user) {
        List<SysDept> result = new ArrayList<>();
        if (user == null || !Integer.valueOf(1).equals(user.getUserType())) {
            return result;
        }
        Set<Long> seen = new LinkedHashSet<>();
        if (user.getDeptId() != null) {
            SysDept main = deptMapper.selectById(user.getDeptId());
            if (main != null && Boolean.TRUE.equals(main.getStatus()) && seen.add(main.getId())) {
                result.add(main);
            }
        }
        userOrgMapper.selectList(Wrappers.<SysUserOrg>lambdaQuery()
                        .eq(SysUserOrg::getUserId, user.getId()))
                .stream()
                .map(SysUserOrg::getOrgId)
                .filter(Objects::nonNull)
                .filter(seen::add)
                .map(deptMapper::selectById)
                .filter(item -> item != null && Boolean.TRUE.equals(item.getStatus()))
                .forEach(result::add);
        return result;
    }

    /**
     * 数据权限锚点：当前机构 → 无则回落主机构。
     * 当前机构不在可登录范围内（被收回授权/停用）时同样回落，避免旧会话越权。
     */
    public Long resolveAnchor(SysUser user, Long currentOrgId, List<SysDept> orgs) {
        if (currentOrgId == null) {
            return user.getDeptId();
        }
        boolean allowed = orgs.stream().anyMatch(item -> Objects.equals(item.getId(), currentOrgId));
        return allowed ? currentOrgId : user.getDeptId();
    }

    /** 拦截器入口：未切换机构时直接返回主机构，避免每请求多查表 */
    public Long anchorDeptId(SysUser user, String token) {
        Long currentOrgId = tokenStore.currentOrgId(token);
        if (currentOrgId == null) {
            return user.getDeptId();
        }
        return resolveAnchor(user, currentOrgId, loginableDepts(user));
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        return user;
    }

    private List<Map<String, Object>> orgMaps(List<SysDept> orgs) {
        List<Map<String, Object>> result = new ArrayList<>();
        orgs.forEach(dept -> result.add(orgOf(dept)));
        return result;
    }

    private String orgName(List<SysDept> orgs, Long orgId) {
        if (orgId == null) {
            return null;
        }
        return orgs.stream()
                .filter(item -> Objects.equals(item.getId(), orgId))
                .map(SysDept::getDeptName)
                .findFirst()
                .orElseGet(() -> {
                    SysDept dept = deptMapper.selectById(orgId);
                    return dept == null ? null : dept.getDeptName();
                });
    }

    /** 内部员工必须有可用机构：所属机构存在且自身与祖先链全部启用 */
    private void checkDeptChain(Long deptId) {
        if (deptId == null) {
            throw new BizException("账号未分配所属机构，暂无法登录，请联系管理员");
        }
        Long cursor = deptId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 20) {
            SysDept dept = deptMapper.selectById(cursor);
            if (dept == null) {
                throw new BizException("所属机构不存在或已删除，暂无法登录");
            }
            if (!Boolean.TRUE.equals(dept.getStatus())) {
                throw new BizException("所属机构已停用，暂无法登录");
            }
            cursor = dept.getParentId();
        }
    }

    private Map<String, Object> orgOf(SysDept dept) {
        Map<String, Object> org = new LinkedHashMap<>();
        org.put("id", dept.getId());
        org.put("name", dept.getDeptName());
        return org;
    }
}
