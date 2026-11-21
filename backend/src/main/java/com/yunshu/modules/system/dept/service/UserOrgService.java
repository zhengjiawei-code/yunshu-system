package com.yunshu.modules.system.dept.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.entity.SysDeptApplyCompany;
import com.yunshu.modules.system.dept.mapper.SysDeptApplyCompanyMapper;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.vo.ManageScopeNodeVO;
import com.yunshu.modules.system.dept.vo.ManageScopeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 登录人公司与管理范围解析（服务端唯一口径）。
 * 总部=全部；分公司=本公司子树 ∪ 适用本公司的末端网点；链断/成环 fail-closed。
 */
@Service
@RequiredArgsConstructor
public class UserOrgService {

    private static final int TYPE_HQ = 1;
    private static final int TYPE_COMPANY = 2;
    private static final int MAX_NODES = 5000;

    private final SysDeptMapper deptMapper;
    private final SysDeptApplyCompanyMapper applyCompanyMapper;

    public record UserOrg(Long userId, Long deptId, boolean hq, Long companyId, String companyName) {
    }

    /** 当前登录人所属组织；异常直接抛 403，避免越权 */
    public UserOrg current() {
        DataScopeContext context = DataScopeContext.current();
        if (context == null || context.getUserId() == null) {
            throw new BizException(403, "登录状态失效，请重新登录");
        }
        return resolve(context.getUserId(), context.getDeptId());
    }

    public UserOrg resolve(Long userId, Long deptId) {
        if (deptId == null) {
            throw new BizException(403, "当前账号未分配主机构，无法确定管理范围");
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(403, "当前账号主机构不存在，无法确定管理范围");
        }
        Set<Long> visited = new HashSet<>();
        SysDept cursor = dept;
        while (cursor != null && visited.add(cursor.getId())) {
            if (Objects.equals(TYPE_HQ, cursor.getDeptType())) {
                return new UserOrg(userId, deptId, true, cursor.getId(), cursor.getDeptName());
            }
            if (Objects.equals(TYPE_COMPANY, cursor.getDeptType())) {
                return new UserOrg(userId, deptId, false, cursor.getId(), cursor.getDeptName());
            }
            Long parentId = cursor.getParentId();
            if (parentId == null || parentId == 0L) {
                return new UserOrg(userId, deptId, true, 1L, "总部");
            }
            cursor = deptMapper.selectById(parentId);
        }
        throw new BizException(403, "机构关系异常（存在环或上级缺失），暂不可用");
    }

    /** 当前账号可管理的机构ID集合；总部=全部，分公司=子树 ∪ 适用末端网点 */
    public Set<Long> manageDeptIds() {
        return manageDeptIds(current());
    }

    public Set<Long> manageDeptIds(UserOrg org) {
        List<SysDept> all = allDepts();
        if (org.hq()) {
            Set<Long> ids = new LinkedHashSet<>();
            all.forEach(dept -> ids.add(dept.getId()));
            return ids;
        }
        Map<Long, SysDept> map = index(all);
        Set<Long> ids = subtree(org.companyId(), map);
        sharedLeafIds(org.companyId()).forEach(ids::add);
        return ids;
    }

    public boolean inManageScope(Long deptId) {
        return deptId != null && manageDeptIds().contains(deptId);
    }

    public List<SysDept> allDepts() {
        return deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getSortOrder)
                .orderByAsc(SysDept::getId));
    }

    public Map<Long, SysDept> deptMap() {
        return index(allDepts());
    }

    public Set<Long> sharedLeafIds(Long companyId) {
        if (companyId == null) {
            return Set.of();
        }
        List<SysDeptApplyCompany> relations = applyCompanyMapper.selectList(
                Wrappers.<SysDeptApplyCompany>lambdaQuery()
                        .eq(SysDeptApplyCompany::getCompanyDeptId, companyId));
        Set<Long> ids = new LinkedHashSet<>();
        relations.forEach(item -> ids.add(item.getLeafDeptId()));
        return ids;
    }

    /** GET /depts/manage-scope：客户端参数不含 companyId，服务端自行判定 */
    public ManageScopeVO manageScope(List<Integer> types, String keyword) {
        ManageScopeVO result = new ManageScopeVO();
        UserOrg org;
        try {
            org = current();
        } catch (BizException e) {
            result.setScopeType("NONE");
            result.setTotal(0);
            return result;
        }
        result.setScopeType(org.hq() ? "HQ" : "COMPANY");
        result.setCompanyId(org.companyId());
        result.setCompanyName(org.companyName());

        List<SysDept> all = allDepts();
        Map<Long, SysDept> map = index(all);
        Set<Long> baseIds = org.hq() ? new HashSet<>(map.keySet()) : subtree(org.companyId(), map);
        Set<Long> shared = org.hq() ? Set.of() : sharedLeafIds(org.companyId());
        baseIds.addAll(shared);

        Set<Integer> typeFilter = new HashSet<>();
        if (types != null) {
            types.stream().filter(Objects::nonNull).forEach(typeFilter::add);
        }
        String text = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;

        List<SysDept> included = all.stream()
                .filter(dept -> baseIds.contains(dept.getId()))
                .filter(dept -> Boolean.TRUE.equals(dept.getStatus()))
                .filter(dept -> typeFilter.isEmpty() || typeFilter.contains(dept.getDeptType()))
                .limit(MAX_NODES + 1L)
                .toList();
        if (included.size() > MAX_NODES) {
            throw new BizException("管理范围超过 " + MAX_NODES + " 个机构，请缩小范围后重试");
        }

        Map<Long, ManageScopeNodeVO> nodes = new HashMap<>();
        included.forEach(dept -> nodes.put(dept.getId(), toNode(dept)));

        List<ManageScopeNodeVO> roots = new ArrayList<>();
        included.forEach(dept -> {
            ManageScopeNodeVO node = nodes.get(dept.getId());
            Long parentId = nearestIncludedParent(dept, map, nodes.keySet());
            if (parentId == null) {
                roots.add(node);
            } else {
                nodes.get(parentId).getChildren().add(node);
            }
        });

        sortTree(roots);
        List<ManageScopeNodeVO> pruned = text == null ? roots : prune(roots, text);
        result.setTree(pruned);
        result.setTotal(count(pruned));

        List<ManageScopeNodeVO> sharedNodes = new ArrayList<>();
        for (Long leafId : shared) {
            SysDept leaf = map.get(leafId);
            if (leaf == null || !Boolean.TRUE.equals(leaf.getStatus())) {
                continue;
            }
            if (typeFilter.isEmpty() || typeFilter.contains(leaf.getDeptType())) {
                sharedNodes.add(toNode(leaf));
            }
        }
        sharedNodes.sort(Comparator.comparing(ManageScopeNodeVO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ManageScopeNodeVO::getId));
        result.setSharedLeaves(sharedNodes);
        return result;
    }

    /* ---------------- 内部实现 ---------------- */

    private Map<Long, SysDept> index(List<SysDept> depts) {
        Map<Long, SysDept> map = new HashMap<>();
        depts.forEach(dept -> map.put(dept.getId(), dept));
        return map;
    }

    private Set<Long> subtree(Long rootId, Map<Long, SysDept> map) {
        Set<Long> result = new LinkedHashSet<>();
        if (rootId == null || !map.containsKey(rootId)) {
            return result;
        }
        Map<Long, List<Long>> children = new HashMap<>();
        map.values().forEach(dept -> children
                .computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(), key -> new ArrayList<>())
                .add(dept.getId()));
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current == null || !result.add(current)) {
                continue;
            }
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return result;
    }

    private Long nearestIncludedParent(SysDept dept, Map<Long, SysDept> map, Set<Long> included) {
        Set<Long> visited = new HashSet<>();
        Long parentId = dept.getParentId();
        while (parentId != null && parentId != 0L && visited.add(parentId)) {
            if (included.contains(parentId)) {
                return parentId;
            }
            SysDept parent = map.get(parentId);
            if (parent == null) {
                return null;
            }
            parentId = parent.getParentId();
        }
        return null;
    }

    private void sortTree(List<ManageScopeNodeVO> nodes) {
        nodes.sort(Comparator.comparing(ManageScopeNodeVO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ManageScopeNodeVO::getId));
        nodes.forEach(node -> sortTree(node.getChildren()));
    }

    private List<ManageScopeNodeVO> prune(List<ManageScopeNodeVO> nodes, String keyword) {
        List<ManageScopeNodeVO> kept = new ArrayList<>();
        for (ManageScopeNodeVO node : nodes) {
            node.setChildren(prune(node.getChildren(), keyword));
            boolean match = node.getDeptName() != null
                    && node.getDeptName().toLowerCase(Locale.ROOT).contains(keyword);
            if (match || !node.getChildren().isEmpty()) {
                kept.add(node);
            }
        }
        return kept;
    }

    private int count(List<ManageScopeNodeVO> nodes) {
        int total = 0;
        for (ManageScopeNodeVO node : nodes) {
            total += 1 + count(node.getChildren());
        }
        return total;
    }

    private ManageScopeNodeVO toNode(SysDept dept) {
        ManageScopeNodeVO node = new ManageScopeNodeVO();
        node.setId(dept.getId());
        node.setParentId(dept.getParentId());
        node.setDeptName(dept.getDeptName());
        node.setDeptCode(dept.getDeptCode());
        node.setDeptType(dept.getDeptType());
        node.setIsLeaf(dept.getIsLeaf());
        node.setStatus(dept.getStatus());
        node.setSortOrder(dept.getSortOrder());
        return node;
    }
}
