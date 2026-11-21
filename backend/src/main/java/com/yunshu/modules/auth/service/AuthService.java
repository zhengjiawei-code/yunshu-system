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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真实登录与权限下发：BCrypt 校验 + 服务端 token；菜单权限按角色与账号直授实时计算。
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", tokenStore.issue(user.getId()));
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        return data;
    }

    public Map<String, Object> info(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }

        boolean superAdmin = permissionService.isSuperAdmin(user);
        List<String> roles = permissionService.roleCodes(userId);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("userType", user.getUserType());
        userInfo.put("deptId", user.getDeptId());

        SysDept dept = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        String deptName = dept == null ? null : dept.getDeptName();
        userInfo.put("deptName", deptName);
        // 兼容前端既有字段名
        userInfo.put("orgId", user.getDeptId());
        userInfo.put("orgName", deptName);

        List<Map<String, Object>> authOrgs = new ArrayList<>();
        if (Integer.valueOf(1).equals(user.getUserType()) && dept != null) {
            authOrgs.add(orgOf(dept));
            userOrgMapper.selectList(Wrappers.<SysUserOrg>lambdaQuery()
                            .eq(SysUserOrg::getUserId, userId))
                    .stream()
                    .map(SysUserOrg::getOrgId)
                    .filter(orgId -> !orgId.equals(user.getDeptId()))
                    .map(deptMapper::selectById)
                    .filter(extra -> extra != null && Boolean.TRUE.equals(extra.getStatus()))
                    .forEach(extra -> authOrgs.add(orgOf(extra)));
        }
        userInfo.put("authOrgs", authOrgs);

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
