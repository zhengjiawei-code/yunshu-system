package com.yunshu.modules.system.menu.service;

import com.yunshu.modules.system.menu.dto.MenuSaveRequest;
import com.yunshu.modules.system.menu.vo.MenuVO;

import java.util.Collection;
import java.util.List;

public interface SysMenuService {

    /**
     * 管理页全量树：含禁用、隐藏与按钮，不做端过滤。
     * 排序为“同级排序”：只重排每个父节点下的兄弟节点，不破坏层级；
     * sortBy 为空时保持 sort_order asc, id asc。
     */
    List<MenuVO> tree(String sortBy, String sortOrder);

    MenuVO detail(Long id);

    Long create(MenuSaveRequest request);

    void update(Long id, MenuSaveRequest request);

    void delete(Long id);

    void sort(List<Long> ids);

    /** 登录后下发的路由树：只含启用状态的目录/菜单 */
    List<MenuVO> routeTree();

    /** 全部可用按钮权限标识 */
    List<String> buttonPerms();

    /** 按允许的菜单ID下发路由树：只含启用目录/菜单，父链不完整则不提升为根 */
    List<MenuVO> routeTree(Collection<Long> allowedIds);

    /** 按允许的菜单ID下发按钮权限标识 */
    List<String> buttonPerms(Collection<Long> allowedIds);
}
