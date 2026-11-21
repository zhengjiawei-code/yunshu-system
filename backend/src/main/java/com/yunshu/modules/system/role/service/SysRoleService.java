package com.yunshu.modules.system.role.service;

import com.yunshu.modules.system.role.dto.RoleQuery;
import com.yunshu.modules.system.role.dto.RoleSaveRequest;
import com.yunshu.modules.system.role.vo.RolePageVO;
import com.yunshu.modules.system.role.vo.RoleVO;

import java.util.List;

public interface SysRoleService {

    RolePageVO page(RoleQuery query);

    RoleVO detail(Long id);

    Long create(RoleSaveRequest request);

    void update(Long id, RoleSaveRequest request);

    void delete(Long id);

    /** 角色已授权的菜单/按钮ID；admin 返回全量菜单 */
    List<Long> menuIds(Long roleId);

    /** 整体覆盖保存角色菜单授权 */
    void saveMenus(Long roleId, List<Long> menuIds);

    /** 启用角色下拉选项（用户管理绑定角色用） */
    List<RoleVO> options();
}
