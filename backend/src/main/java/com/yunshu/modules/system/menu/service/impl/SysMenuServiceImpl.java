package com.yunshu.modules.system.menu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.auth.service.PermissionService;
import com.yunshu.modules.system.menu.dto.MenuSaveRequest;
import com.yunshu.modules.system.menu.entity.SysMenu;
import com.yunshu.modules.system.menu.mapper.SysMenuMapper;
import com.yunshu.modules.system.menu.service.SysMenuService;
import com.yunshu.modules.system.menu.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private static final int DIR = 1;
    private static final int MENU = 2;
    private static final int BUTTON = 3;
    private static final int MAX_DEPTH = 4;

    private final SysMenuMapper menuMapper;
    private final PermissionService permissionService;

    @Override
    @DS("slave")
    public List<MenuVO> tree(String sortBy, String sortOrder) {
        return buildTree(selectAll(), buildComparator(sortBy, sortOrder));
    }

    @Override
    @DS("slave")
    public MenuVO detail(Long id) {
        return toVO(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long create(MenuSaveRequest request) {
        SysMenu node = new SysMenu();
        applyRequest(node, request);
        validate(node, null);
        menuMapper.insert(node);
        permissionService.evictAll();
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, MenuSaveRequest request) {
        SysMenu existing = require(id);
        if (!Objects.equals(existing.getMenuType(), request.getMenuType())) {
            throw new BizException("节点类型不允许修改，如需变更请删除后重建");
        }

        boolean hasChildren = hasChildren(id);
        // 目录有子节点后禁止改路由，避免子孙页面路径全部失效
        if (existing.getMenuType() == DIR && hasChildren
                && !Objects.equals(normalize(existing.getRoutePath()), normalize(request.getRoutePath()))) {
            throw new BizException("该目录下已有子节点，不允许修改路由地址");
        }

        if (!Objects.equals(existing.getParentId(), request.getParentId())) {
            checkReparent(id, request);
        }

        SysMenu node = new SysMenu();
        applyRequest(node, request);
        node.setId(id);
        validate(node, id);
        // 显式 set 全部业务字段：清空组件、图标等场景需要把列更新为 NULL
        menuMapper.update(null, Wrappers.<SysMenu>lambdaUpdate()
                .eq(SysMenu::getId, id)
                .set(SysMenu::getParentId, node.getParentId())
                .set(SysMenu::getMenuName, node.getMenuName())
                .set(SysMenu::getMenuType, node.getMenuType())
                .set(SysMenu::getRoutePath, node.getRoutePath())
                .set(SysMenu::getComponent, node.getComponent())
                .set(SysMenu::getRedirect, node.getRedirect())
                .set(SysMenu::getPerms, node.getPerms())
                .set(SysMenu::getIcon, node.getIcon())
                .set(SysMenu::getTerminalType, node.getTerminalType())
                .set(SysMenu::getSortOrder, node.getSortOrder())
                .set(SysMenu::getVisible, node.getVisible())
                .set(SysMenu::getStatus, node.getStatus())
                .set(SysMenu::getKeepAlive, node.getKeepAlive())
                .set(SysMenu::getAffix, node.getAffix())
                .set(SysMenu::getRemark, node.getRemark()));
        permissionService.evictAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id) {
        require(id);
        if (hasChildren(id)) {
            throw new BizException("该节点下存在子节点，请先删除子节点");
        }
        menuMapper.deleteById(id);
        permissionService.evictAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void sort(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("排序列表不能为空");
        }
        int order = 10;
        for (Long id : ids) {
            SysMenu update = new SysMenu();
            update.setId(id);
            update.setSortOrder(order);
            menuMapper.updateById(update);
            order += 10;
        }
    }

    @Override
    @DS("slave")
    public List<MenuVO> routeTree() {
        List<SysMenu> all = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, true)
                .in(SysMenu::getMenuType, DIR, MENU)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        return buildTree(all, null);
    }

    @Override
    @DS("slave")
    public List<String> buttonPerms() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                        .select(SysMenu::getPerms)
                        .eq(SysMenu::getMenuType, BUTTON)
                        .eq(SysMenu::getStatus, true)
                        .isNotNull(SysMenu::getPerms))
                .stream()
                .map(SysMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Override
    @DS("slave")
    public List<MenuVO> routeTree(Collection<Long> allowedIds) {
        if (allowedIds == null || allowedIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> enabled = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, true)
                .in(SysMenu::getMenuType, DIR, MENU)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        Map<Long, SysMenu> index = new LinkedHashMap<>();
        enabled.forEach(menu -> index.put(menu.getId(), menu));

        // 补祖先链：祖先被禁用或不存在时链条自然断开，子节点不会提升为根
        Set<Long> visible = new HashSet<>();
        for (Long id : allowedIds) {
            Long cursor = id;
            int guard = 0;
            while (cursor != null && cursor != 0L && guard++ < 20) {
                SysMenu node = index.get(cursor);
                if (node == null) {
                    break;
                }
                visible.add(cursor);
                cursor = node.getParentId();
            }
        }
        List<SysMenu> filtered = enabled.stream()
                .filter(menu -> visible.contains(menu.getId()))
                .toList();
        return buildTree(filtered, null);
    }

    @Override
    @DS("slave")
    public List<String> buttonPerms(Collection<Long> allowedIds) {
        if (allowedIds == null || allowedIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                        .select(SysMenu::getId, SysMenu::getPerms)
                        .eq(SysMenu::getMenuType, BUTTON)
                        .eq(SysMenu::getStatus, true)
                        .isNotNull(SysMenu::getPerms))
                .stream()
                .filter(menu -> allowedIds.contains(menu.getId()))
                .map(SysMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /* ---------------- 内部实现 ---------------- */

    private List<SysMenu> selectAll() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
    }

    private SysMenu require(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException("菜单不存在或已删除");
        }
        return menu;
    }

    private boolean hasChildren(Long id) {
        return menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id)) > 0;
    }

    /** 按 menuType 归一化字段，避免按钮写路由、目录写组件等脏数据 */
    private void applyRequest(SysMenu node, MenuSaveRequest request) {
        int type = request.getMenuType();

        node.setParentId(request.getParentId());
        node.setMenuType(type);
        node.setMenuName(request.getMenuName().trim());
        node.setTerminalType(StringUtils.hasText(request.getTerminalType())
                ? request.getTerminalType().trim() : "PC");
        node.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSort(request.getParentId()));
        node.setVisible(request.getVisible() == null || request.getVisible());
        node.setStatus(request.getStatus() == null || request.getStatus());
        node.setRemark(request.getRemark());

        if (type == DIR) {
            node.setRoutePath(normalize(request.getRoutePath()));
            node.setComponent(null);
            node.setRedirect(normalize(request.getRedirect()));
            node.setPerms(null);
            node.setIcon(normalize(request.getIcon()));
            node.setKeepAlive(false);
            node.setAffix(false);
        } else if (type == MENU) {
            node.setRoutePath(normalize(request.getRoutePath()));
            node.setComponent(normalize(request.getComponent()));
            node.setRedirect(null);
            node.setPerms(StringUtils.hasText(request.getPerms()) ? request.getPerms().trim() : null);
            node.setIcon(normalize(request.getIcon()));
            node.setKeepAlive(request.getKeepAlive() == null || request.getKeepAlive());
            node.setAffix(request.getAffix() != null && request.getAffix());
        } else {
            node.setRoutePath(null);
            node.setComponent(null);
            node.setRedirect(null);
            node.setPerms(StringUtils.hasText(request.getPerms()) ? request.getPerms().trim() : null);
            node.setIcon(null);
            node.setKeepAlive(false);
            node.setAffix(false);
        }
    }

    private Integer nextSort(Long parentId) {
        return selectAll().stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(SysMenu::getSortOrder)
                .max(Comparator.naturalOrder())
                .map(max -> max + 10)
                .orElse(10);
    }

    private void validate(SysMenu node, Long excludeId) {
        if (!List.of("PC", "APP", "PC,APP").contains(node.getTerminalType())) {
            throw new BizException("适用终端只能是 PC、APP 或 PC,APP");
        }
        checkParent(node);
        checkDepth(node);
        checkNameUnique(node, excludeId);
        checkRouteUnique(node, excludeId);
        checkPerms(node, excludeId);
    }

    private void checkParent(SysMenu node) {
        Long parentId = node.getParentId();
        int type = node.getMenuType();
        if (parentId == null || parentId == 0L) {
            if (type != DIR) {
                throw new BizException("只有目录可以挂在顶级，菜单必须选择上级目录，按钮必须选择上级菜单");
            }
            return;
        }
        SysMenu parent = menuMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException("上级节点不存在或已删除");
        }
        if (type == DIR && parent.getMenuType() != DIR) {
            throw new BizException("目录的上级只能是顶级或目录");
        }
        if (type == MENU && parent.getMenuType() != DIR) {
            throw new BizException("菜单的上级只能是目录");
        }
        if (type == BUTTON && parent.getMenuType() != MENU) {
            throw new BizException("按钮的上级只能是菜单");
        }
    }

    /** 目录/菜单最多 4 级，按钮不占用层级 */
    private void checkDepth(SysMenu node) {
        if (node.getMenuType() == BUTTON) {
            return;
        }
        int depth = 1;
        Long parentId = node.getParentId();
        while (parentId != null && parentId != 0L) {
            depth++;
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null) {
                break;
            }
            parentId = parent.getParentId();
        }
        if (depth > MAX_DEPTH) {
            throw new BizException("层级最多 " + MAX_DEPTH + " 级（不含按钮），当前已超出");
        }
    }

    private void checkReparent(Long id, MenuSaveRequest request) {
        if (request.getParentId().equals(id)) {
            throw new BizException("上级节点不能是自己");
        }
        if (isDescendant(request.getParentId(), id)) {
            throw new BizException("上级节点不能是自己的子节点");
        }
        if (request.getMenuType() != BUTTON) {
            int parentDepth = depthOf(request.getParentId());
            if (parentDepth + subtreeHeight(id) > MAX_DEPTH) {
                throw new BizException("移动后层级超过 " + MAX_DEPTH + " 级（不含按钮），请调整目标位置");
            }
        }
    }

    private boolean isDescendant(Long candidateId, Long ancestorId) {
        Long cursor = candidateId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 50) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            SysMenu node = menuMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }

    private int depthOf(Long id) {
        if (id == null || id == 0L) {
            return 0;
        }
        int depth = 1;
        Long cursor = id;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 50) {
            SysMenu node = menuMapper.selectById(cursor);
            if (node == null) {
                break;
            }
            if (node.getParentId() != null && node.getParentId() != 0L) {
                depth++;
            }
            cursor = node.getParentId();
        }
        return depth;
    }

    /** 以自身为根的子树高度（按钮层不计） */
    private int subtreeHeight(Long id) {
        List<SysMenu> children = menuMapper.selectList(
                Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id));
        int max = 0;
        for (SysMenu child : children) {
            if (child.getMenuType() == BUTTON) {
                continue;
            }
            max = Math.max(max, subtreeHeight(child.getId()));
        }
        return max + 1;
    }

    private void checkNameUnique(SysMenu node, Long excludeId) {
        Long count = menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, node.getParentId())
                .eq(SysMenu::getMenuName, node.getMenuName())
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (count > 0) {
            throw new BizException("同一上级下已存在同名节点：" + node.getMenuName());
        }
    }

    private void checkRouteUnique(SysMenu node, Long excludeId) {
        if (!StringUtils.hasText(node.getRoutePath())) {
            if (node.getMenuType() != BUTTON) {
                throw new BizException("目录与菜单必须填写路由地址");
            }
            return;
        }
        Long count = menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getRoutePath, node.getRoutePath())
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (count > 0) {
            throw new BizException("路由地址已被占用：" + node.getRoutePath());
        }
    }

    private void checkPerms(SysMenu node, Long excludeId) {
        if (node.getMenuType() == BUTTON && !StringUtils.hasText(node.getPerms())) {
            throw new BizException("按钮必须填写权限标识");
        }
        if (!StringUtils.hasText(node.getPerms())) {
            return;
        }
        Long count = menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getPerms, node.getPerms())
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (count > 0) {
            throw new BizException("权限标识已存在：" + node.getPerms());
        }
    }

    /**
     * 菜单是树形表格，排序只作用于“同级兄弟节点”，层级与缩进保持不变。
     * 未指定 sortBy 时按 sort_order asc, id asc。
     */
    private Comparator<MenuVO> buildComparator(String sortBy, String sortOrder) {
        Comparator<MenuVO> fallback = Comparator
                .comparing(MenuVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MenuVO::getId);
        if (!StringUtils.hasText(sortBy)) {
            return fallback;
        }
        Comparator<MenuVO> comparator = switch (sortBy) {
            case "menuName" -> Comparator.comparing(MenuVO::getMenuName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "menuType" -> Comparator.comparing(MenuVO::getMenuType,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "routePath" -> Comparator.comparing(MenuVO::getRoutePath,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "component" -> Comparator.comparing(MenuVO::getComponent,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "perms" -> Comparator.comparing(MenuVO::getPerms,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "terminalType" -> Comparator.comparing(MenuVO::getTerminalType,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "sortOrder" -> Comparator.comparing(MenuVO::getSortOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "visible" -> Comparator.comparing(MenuVO::getVisible,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(MenuVO::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateTime" -> Comparator.comparing(MenuVO::getUpdateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator == null) {
            return fallback;
        }
        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(MenuVO::getId);
    }

    private List<MenuVO> buildTree(List<SysMenu> all, Comparator<MenuVO> comparator) {
        Comparator<MenuVO> order = comparator != null ? comparator : Comparator
                .comparing(MenuVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MenuVO::getId);
        Map<Long, MenuVO> index = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            MenuVO vo = toVO(menu);
            vo.setChildren(new ArrayList<>());
            index.put(vo.getId(), vo);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO vo : index.values()) {
            Long parentId = vo.getParentId();
            MenuVO parent = parentId == null || parentId == 0L ? null : index.get(parentId);
            if (parent == null) {
                if (parentId == null || parentId == 0L) {
                    roots.add(vo);
                }
                continue;
            }
            parent.getChildren().add(vo);
        }
        roots.sort(order);
        for (MenuVO vo : index.values()) {
            if (vo.getChildren() != null) {
                vo.getChildren().sort(order);
                if (vo.getChildren().isEmpty()) {
                    vo.setChildren(null);
                }
            }
        }
        return roots;
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setRoutePath(menu.getRoutePath());
        vo.setComponent(menu.getComponent());
        vo.setRedirect(menu.getRedirect());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setTerminalType(menu.getTerminalType());
        vo.setSortOrder(menu.getSortOrder());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setKeepAlive(menu.getKeepAlive());
        vo.setAffix(menu.getAffix());
        vo.setRemark(menu.getRemark());
        vo.setUpdateTime(menu.getUpdateTime());
        return vo;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
