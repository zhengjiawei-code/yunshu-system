package com.yunshu.modules.system.dept.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.dept.dto.DeptQuery;
import com.yunshu.modules.system.dept.dto.DeptSaveRequest;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.entity.SysDeptApplyCompany;
import com.yunshu.modules.system.dept.mapper.SysDeptApplyCompanyMapper;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.service.SysDeptService;
import com.yunshu.modules.system.region.entity.SysRegion;
import com.yunshu.modules.system.region.mapper.SysRegionMapper;
import com.yunshu.modules.system.dept.vo.DeptPageVO;
import com.yunshu.modules.system.dept.vo.DeptRowVO;
import com.yunshu.modules.system.dept.vo.DeptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl implements SysDeptService {

    /** 内置总部ID：全库唯一根，不可删除/禁用/移动/改类型 */
    private static final long HQ_ID = 1L;
    private static final int TYPE_HQ = 1;
    private static final int TYPE_OUTLET = 5;
    private static final int MAX_DEPTH = 5;

    /** 父子允许矩阵：父类型 → 允许的子类型；线路不在机构树内，由路由管理定义 */
    private static final Map<Integer, Set<Integer>> ALLOWED_CHILD = Map.of(
            1, Set.of(2, 3, 4),
            2, Set.of(2, 3, 4, 5),
            3, Set.of(3),
            4, Set.of(3, 5),
            5, Set.of(3)
    );

    private static final Map<Integer, String> TYPE_NAME = Map.of(
            1, "总部",
            2, "公司",
            3, "部门",
            4, "分拨中心",
            5, "网点"
    );

    private final SysDeptMapper deptMapper;
    private final SysRegionMapper regionMapper;
    private final SysDeptApplyCompanyMapper applyCompanyMapper;

    @Override
    @DS("slave")
    public List<DeptVO> tree() {
        List<SysDept> list = selectAll();
        Map<Long, SysDept> map = new LinkedHashMap<>();
        list.forEach(item -> map.put(item.getId(), item));
        return buildTree(list, regionNameMap(), applyCompanyMap(), map);
    }

    @Override
    @DS("slave")
    public DeptPageVO page(DeptQuery query) {
        Map<Long, SysDept> map = loadMap();
        Long rootId = query.getRootId() == null ? HQ_ID : query.getRootId();
        List<SysDept> descendants = new ArrayList<>();
        collectDescendants(rootId, map, descendants);

        // sys_user 未建表时：姓名列显示“—”，姓名筛选项整体忽略（不能把所有行过滤掉），用户表建好后自动生效
        boolean userTableReady = deptMapper.countTable("sys_user") > 0;
        Map<Long, String> userNames = userTableReady ? resolveUserNames(descendants) : Map.of();
        Map<String, String> regionNames = regionNameMap();
        Map<Long, List<Long>> applyCompanies = applyCompanyMap();

        String name = trim(query.getDeptName());
        String code = trim(query.getDeptCode());
        String leader = trim(query.getLeader());
        String phone = trim(query.getPhone());
        String createByName = trim(query.getCreateByName());
        String updateByName = trim(query.getUpdateByName());
        String provinceCode = trim(query.getProvinceCode());
        String cityCode = trim(query.getCityCode());
        String districtCode = trim(query.getDistrictCode());
        LocalDateTime createStart = parseStart(query.getCreateTimeStart());
        LocalDateTime createEnd = parseEnd(query.getCreateTimeEnd());
        LocalDateTime updateStart = parseStart(query.getUpdateTimeStart());
        LocalDateTime updateEnd = parseEnd(query.getUpdateTimeEnd());

        List<SysDept> filtered = descendants.stream()
                .filter(item -> name == null || item.getDeptName().toLowerCase().contains(name.toLowerCase()))
                .filter(item -> code == null || item.getDeptCode().toLowerCase().contains(code.toLowerCase()))
                .filter(item -> leader == null || (item.getLeader() != null
                        && item.getLeader().toLowerCase().contains(leader.toLowerCase())))
                .filter(item -> phone == null || (item.getPhone() != null
                        && item.getPhone().toLowerCase().contains(phone.toLowerCase())))
                .filter(item -> createByName == null || !userTableReady
                        || containsIgnoreCase(nameOf(userNames, item.getCreateBy()), createByName))
                .filter(item -> updateByName == null || !userTableReady
                        || containsIgnoreCase(nameOf(userNames, item.getUpdateBy()), updateByName))
                .filter(item -> createStart == null
                        || (item.getCreateTime() != null && !item.getCreateTime().isBefore(createStart)))
                .filter(item -> createEnd == null
                        || (item.getCreateTime() != null && !item.getCreateTime().isAfter(createEnd)))
                .filter(item -> updateStart == null
                        || (item.getUpdateTime() != null && !item.getUpdateTime().isBefore(updateStart)))
                .filter(item -> updateEnd == null
                        || (item.getUpdateTime() != null && !item.getUpdateTime().isAfter(updateEnd)))
                .filter(item -> query.getDeptType() == null
                        || Objects.equals(item.getDeptType(), query.getDeptType()))
                .filter(item -> query.getDeptNature() == null
                        || Objects.equals(item.getDeptNature(), query.getDeptNature()))
                .filter(item -> query.getParentId() == null
                        || Objects.equals(item.getParentId(), query.getParentId()))
                .filter(item -> query.getIsLeaf() == null
                        || Objects.equals(Boolean.TRUE.equals(item.getIsLeaf()), query.getIsLeaf()))
                .filter(item -> query.getStatus() == null
                        || Objects.equals(item.getStatus(), query.getStatus()))
                .filter(item -> provinceCode == null || provinceCode.equals(item.getProvinceCode()))
                .filter(item -> cityCode == null || cityCode.equals(item.getCityCode()))
                .filter(item -> districtCode == null || districtCode.equals(item.getDistrictCode()))
                .filter(item -> query.getApplyCompanyId() == null
                        || (Boolean.TRUE.equals(item.getIsLeaf())
                        && applyCompanies.getOrDefault(item.getId(), List.of()).contains(query.getApplyCompanyId())))
                .sorted(buildComparator(query, map, userNames, regionNames, applyCompanies))
                .toList();

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 10000);
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());

        List<DeptRowVO> records = filtered.subList(from, to).stream()
                .map(item -> toRow(item, map, userNames, regionNames, applyCompanies))
                .toList();

        DeptPageVO result = new DeptPageVO();
        result.setRecords(records);
        result.setTotal(filtered.size());
        return result;
    }

    @Override
    @DS("slave")
    public DeptVO detail(Long id) {
        SysDept dept = require(id);
        DeptVO vo = new DeptVO();
        BeanUtils.copyProperties(dept, vo);
        Map<Long, String> names = resolveUserNames(List.of(dept));
        vo.setCreateByName(nameOf(names, dept.getCreateBy()));
        vo.setUpdateByName(nameOf(names, dept.getUpdateBy()));
        fillRegionNames(vo, regionNameMap());
        Map<Long, SysDept> map = loadMap();
        List<Long> applyIds = applyCompaniesFor(id);
        vo.setApplyCompanyIds(applyIds);
        vo.setApplyCompanyNames(joinCompanyNames(applyIds, map));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long create(DeptSaveRequest request) {
        Map<Long, SysDept> map = loadMap();
        Long parentId = request.getParentId();
        int type = request.getDeptType();

        if (parentId == null || parentId <= 0) {
            throw new BizException("请选择上级机构");
        }
        if (type == TYPE_HQ) {
            throw new BizException("系统只允许一个总部，不能新增总部");
        }
        SysDept parent = map.get(parentId);
        if (parent == null) {
            throw new BizException("上级机构不存在");
        }
        if (!Boolean.TRUE.equals(parent.getStatus())) {
            throw new BizException("上级机构已禁用，不能在其下新增机构");
        }
        checkMatrix(parent.getDeptType(), type);
        checkLeaf(type, request.getIsLeaf());
        checkNature(request.getDeptNature());
        validateRegion(type, request);
        boolean leaf = type == TYPE_OUTLET && Boolean.TRUE.equals(request.getIsLeaf());
        List<Long> applyCompanyIds = validateApplyCompanies(leaf, request.getApplyCompanyIds(), map);

        int depth = depthOf(parentId, map) + 1;
        if (depth > MAX_DEPTH) {
            throw new BizException("机构层级最多 5 级，当前已超出");
        }

        String name = trim(request.getDeptName());
        String code = trim(request.getDeptCode());
        checkNameUnique(map, parentId, name, null);
        checkCodeUnique(map, code, null);

        SysDept node = new SysDept();
        node.setParentId(parentId);
        node.setDeptName(name);
        node.setDeptCode(code);
        node.setDeptType(type);
        node.setDeptNature(request.getDeptNature());
        node.setIsLeaf(leaf);
        node.setAllowShip(request.getAllowShip() == null || request.getAllowShip());
        node.setAllowArrival(request.getAllowArrival() == null || request.getAllowArrival());
        node.setAllowPrepaidUnpaid(Boolean.TRUE.equals(request.getAllowPrepaidUnpaid()));
        node.setAllowCollectUnpaidSign(Boolean.TRUE.equals(request.getAllowCollectUnpaidSign()));
        node.setLeader(trim(request.getLeader()));
        node.setPhone(trim(request.getPhone()));
        node.setAddress(trim(request.getAddress()));
        node.setProvinceCode(trim(request.getProvinceCode()));
        node.setCityCode(trim(request.getCityCode()));
        node.setDistrictCode(trim(request.getDistrictCode()));
        node.setSortOrder(request.getSortOrder());
        node.setStatus(request.getStatus() == null || request.getStatus());
        node.setRemark(trim(request.getRemark()));

        deptMapper.insert(node);
        saveApplyCompanies(node.getId(), applyCompanyIds);
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, DeptSaveRequest request) {
        SysDept existing = require(id);
        String code = trim(request.getDeptCode());
        if (!Objects.equals(code, existing.getDeptCode())) {
            throw new BizException("机构编码创建后不可修改");
        }

        Map<Long, SysDept> map = loadMap();
        int type = request.getDeptType();
        Long parentId = request.getParentId();
        String name = trim(request.getDeptName());
        checkLeaf(type, request.getIsLeaf());
        checkNature(request.getDeptNature());
        // 是否末端只对网点有效：类型不是网点时强制清空；未传时保留原值
        boolean leaf = type == TYPE_OUTLET
                && (request.getIsLeaf() == null
                    ? Boolean.TRUE.equals(existing.getIsLeaf())
                    : request.getIsLeaf());
        validateRegion(type, request);
        List<Long> applyCompanyIds = validateApplyCompanies(leaf, request.getApplyCompanyIds(), map);

        if (id == HQ_ID) {
            // 总部保护：不可移动、类型不可改、不可禁用；名称等展示字段可维护
            if (!Objects.equals(parentId, 0L)) {
                throw new BizException("系统只允许一个总部，不能移动总部");
            }
            if (type != TYPE_HQ) {
                throw new BizException("内置总部的机构类型不可修改");
            }
            if (Boolean.FALSE.equals(request.getStatus())) {
                throw new BizException("内置总部不可禁用");
            }
            // 总部固定自营：不允许通过任何入口改成加盟/合作
            if (!Objects.equals(request.getDeptNature(), 1)) {
                throw new BizException("内置总部的机构性质固定为自营，不可修改");
            }
        } else {
            if (parentId == null || parentId <= 0) {
                throw new BizException("请选择上级机构");
            }
            SysDept parent = map.get(parentId);
            if (parent == null) {
                throw new BizException("上级机构不存在");
            }
            if (Objects.equals(parentId, id) || isDescendant(id, parentId, map)) {
                throw new BizException("上级机构不能是自己或自己的子机构");
            }
            boolean reparent = !Objects.equals(parentId, existing.getParentId());
            if (reparent && !Boolean.TRUE.equals(parent.getStatus())) {
                throw new BizException("上级机构已禁用，不能在其下新增机构");
            }
            checkMatrix(parent.getDeptType(), type);

            int newDepth = depthOf(parentId, map) + 1;
            if (newDepth + subtreeHeight(id, map) > MAX_DEPTH) {
                throw new BizException("机构层级最多 5 级，当前已超出");
            }

            // 类型改动需两段校验：父→新类型（上面已校验）与新类型→所有现有子节点
            if (!Objects.equals(existing.getDeptType(), type)) {
                List<String> conflicts = new ArrayList<>();
                map.values().stream()
                        .filter(item -> Objects.equals(item.getParentId(), id))
                        .filter(item -> !isChildAllowed(type, item.getDeptType()))
                        .forEach(item -> conflicts.add(item.getDeptName() + "（" + typeName(item.getDeptType()) + "）"));
                if (!conflicts.isEmpty()) {
                    throw new BizException(typeName(type) + "下不允许存在以下子机构："
                            + String.join("、", conflicts) + "，请先调整子机构或父子关系");
                }
            }
        }

        checkNameUnique(map, parentId, name, id);

        SysDept node = new SysDept();
        node.setParentId(parentId);
        node.setDeptName(name);
        node.setDeptCode(code);
        node.setDeptType(type);
        // 总部固定自营，其余机构按提交值
        node.setDeptNature(id == HQ_ID ? 1 : request.getDeptNature());
        node.setIsLeaf(leaf);
        // 授权开关已独立到「机构授权」抽屉：基础信息保存未携带时保留原值，避免误重置
        node.setAllowShip(request.getAllowShip() == null ? existing.getAllowShip() : request.getAllowShip());
        node.setAllowArrival(request.getAllowArrival() == null ? existing.getAllowArrival() : request.getAllowArrival());
        node.setAllowPrepaidUnpaid(request.getAllowPrepaidUnpaid() == null
                ? existing.getAllowPrepaidUnpaid() : request.getAllowPrepaidUnpaid());
        node.setAllowCollectUnpaidSign(request.getAllowCollectUnpaidSign() == null
                ? existing.getAllowCollectUnpaidSign() : request.getAllowCollectUnpaidSign());
        node.setLeader(trim(request.getLeader()));
        node.setPhone(trim(request.getPhone()));
        node.setAddress(trim(request.getAddress()));
        node.setProvinceCode(trim(request.getProvinceCode()));
        node.setCityCode(trim(request.getCityCode()));
        node.setDistrictCode(trim(request.getDistrictCode()));
        node.setSortOrder(request.getSortOrder());
        node.setStatus(request.getStatus() == null || request.getStatus());
        node.setRemark(trim(request.getRemark()));

        // 显式 set 全部业务字段：负责人、电话等允许被清空
        deptMapper.update(null, Wrappers.<SysDept>lambdaUpdate()
                .eq(SysDept::getId, id)
                .set(SysDept::getParentId, node.getParentId())
                .set(SysDept::getDeptName, node.getDeptName())
                .set(SysDept::getDeptCode, node.getDeptCode())
                .set(SysDept::getDeptType, node.getDeptType())
                .set(SysDept::getDeptNature, node.getDeptNature())
                .set(SysDept::getIsLeaf, node.getIsLeaf())
                .set(SysDept::getAllowShip, node.getAllowShip())
                .set(SysDept::getAllowArrival, node.getAllowArrival())
                .set(SysDept::getAllowPrepaidUnpaid, node.getAllowPrepaidUnpaid())
                .set(SysDept::getAllowCollectUnpaidSign, node.getAllowCollectUnpaidSign())
                .set(SysDept::getLeader, node.getLeader())
                .set(SysDept::getPhone, node.getPhone())
                .set(SysDept::getAddress, node.getAddress())
                .set(SysDept::getProvinceCode, node.getProvinceCode())
                .set(SysDept::getCityCode, node.getCityCode())
                .set(SysDept::getDistrictCode, node.getDistrictCode())
                .set(SysDept::getSortOrder, node.getSortOrder())
                .set(SysDept::getStatus, node.getStatus())
                .set(SysDept::getRemark, node.getRemark()));
        replaceApplyCompanies(id, leaf, applyCompanyIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id) {
        if (id == HQ_ID) {
            throw new BizException("内置总部不允许删除");
        }
        require(id);
        if (hasChildren(id)) {
            throw new BizException("该机构下存在子机构，请先删除子机构");
        }
        if (countUserRef(id) > 0) {
            throw new BizException("该机构下存在用户（主机构或可登录机构），请先调整用户归属");
        }
        applyCompanyMapper.delete(Wrappers.<SysDeptApplyCompany>lambdaQuery()
                .eq(SysDeptApplyCompany::getLeafDeptId, id));
        deptMapper.deleteById(id);
    }

    @Override
    @DS("slave")
    public List<DeptVO> options(Integer type) {
        List<SysDept> list = selectAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getStatus()))
                .filter(item -> type == null || Objects.equals(item.getDeptType(), type))
                .toList();
        Map<Long, SysDept> map = new LinkedHashMap<>();
        selectAll().forEach(item -> map.put(item.getId(), item));
        List<DeptVO> tree = buildTree(list, regionNameMap(), applyCompanyMap(), map);
        pruneToOption(tree);
        return tree;
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
            SysDept update = new SysDept();
            update.setId(id);
            update.setSortOrder(order);
            deptMapper.updateById(update);
            order += 10;
        }
    }

    /* ---------------- 内部实现 ---------------- */

    private List<SysDept> selectAll() {
        return deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getSortOrder)
                .orderByAsc(SysDept::getId));
    }

    private Map<Long, SysDept> loadMap() {
        Map<Long, SysDept> map = new LinkedHashMap<>();
        selectAll().forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private SysDept require(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException("机构不存在或已删除");
        }
        return dept;
    }

    private boolean hasChildren(Long id) {
        return deptMapper.selectCount(Wrappers.<SysDept>lambdaQuery().eq(SysDept::getParentId, id)) > 0;
    }

    /** 删除拦截：sys_user / sys_user_org 属于后续模块，未建表时跳过 */
    private long countUserRef(Long id) {
        long count = 0;
        if (deptMapper.countTable("sys_user") > 0) {
            count += deptMapper.countUserRef(id);
        }
        if (deptMapper.countTable("sys_user_org") > 0) {
            count += deptMapper.countUserOrgRef(id);
        }
        return count;
    }

    private List<DeptVO> buildTree(List<SysDept> list, Map<String, String> regionNames,
                                   Map<Long, List<Long>> applyCompanies, Map<Long, SysDept> map) {
        Map<Long, DeptVO> voMap = new LinkedHashMap<>();
        list.forEach(item -> voMap.put(item.getId(),
                toVO(item, regionNames, applyCompanies, map)));

        List<DeptVO> roots = new ArrayList<>();
        voMap.values().forEach(vo -> {
            DeptVO parent = vo.getParentId() == 0 ? null : voMap.get(vo.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(vo);
            }
        });
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<DeptVO> nodes) {
        nodes.sort(Comparator.comparing(DeptVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DeptVO::getId));
        nodes.forEach(node -> {
            if (node.getChildren() != null) {
                sortTree(node.getChildren());
            }
        });
    }

    private DeptVO toVO(SysDept dept, Map<String, String> regionNames,
                        Map<Long, List<Long>> applyCompanies, Map<Long, SysDept> map) {
        DeptVO vo = new DeptVO();
        BeanUtils.copyProperties(dept, vo);
        fillRegionNames(vo, regionNames);
        List<Long> applyIds = applyCompanies.getOrDefault(dept.getId(), List.of());
        vo.setApplyCompanyIds(applyIds);
        vo.setApplyCompanyNames(joinCompanyNames(applyIds, map));
        return vo;
    }

    /** 收集某机构的全部后代（不含自身） */
    private void collectDescendants(Long rootId, Map<Long, SysDept> map, List<SysDept> out) {
        for (SysDept dept : map.values()) {
            if (Objects.equals(dept.getParentId(), rootId)) {
                out.add(dept);
                collectDescendants(dept.getId(), map, out);
            }
        }
    }

    private DeptRowVO toRow(SysDept dept, Map<Long, SysDept> map, Map<Long, String> userNames,
                            Map<String, String> regionNames, Map<Long, List<Long>> applyCompanies) {
        DeptRowVO row = new DeptRowVO();
        BeanUtils.copyProperties(dept, row);
        SysDept parent = map.get(dept.getParentId());
        row.setParentName(parent == null ? null : parent.getDeptName());
        row.setParentPath(buildPath(dept, map));
        row.setCreateByName(nameOf(userNames, dept.getCreateBy()));
        row.setUpdateByName(nameOf(userNames, dept.getUpdateBy()));
        fillRegionNames(row, regionNames);
        row.setRegionName(regionNameOf(dept, regionNames));
        List<Long> applyIds = applyCompanies.getOrDefault(dept.getId(), List.of());
        row.setApplyCompanyIds(applyIds);
        row.setApplyCompanyNames(joinCompanyNames(applyIds, map));
        return row;
    }

    private void fillRegionNames(DeptVO vo, Map<String, String> regionNames) {
        vo.setProvinceName(nameOfRegion(regionNames, vo.getProvinceCode()));
        vo.setCityName(nameOfRegion(regionNames, vo.getCityCode()));
        vo.setDistrictName(nameOfRegion(regionNames, vo.getDistrictCode()));
    }

    private String nameOfRegion(Map<String, String> regionNames, String code) {
        return StringUtils.hasText(code) ? regionNames.get(code) : null;
    }

    private String regionNameOf(SysDept dept, Map<String, String> regionNames) {
        String province = nameOfRegion(regionNames, dept.getProvinceCode());
        String city = nameOfRegion(regionNames, dept.getCityCode());
        String district = nameOfRegion(regionNames, dept.getDistrictCode());
        if (province == null && city == null && district == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (province != null) {
            text.append(province);
        }
        if (city != null && !Objects.equals(city, province)) {
            text.append(city);
        }
        if (district != null) {
            text.append(district);
        }
        return text.toString();
    }

    private Map<String, String> regionNameMap() {
        Map<String, String> names = new HashMap<>();
        regionMapper.selectList(null).forEach(item -> names.put(item.getCode(), item.getName()));
        return names;
    }

    private Map<Long, List<Long>> applyCompanyMap() {
        Map<Long, List<Long>> result = new HashMap<>();
        applyCompanyMapper.selectList(null).forEach(item -> result
                .computeIfAbsent(item.getLeafDeptId(), key -> new ArrayList<>())
                .add(item.getCompanyDeptId()));
        return result;
    }

    private List<Long> applyCompaniesFor(Long leafDeptId) {
        return applyCompanyMapper.selectList(Wrappers.<SysDeptApplyCompany>lambdaQuery()
                        .eq(SysDeptApplyCompany::getLeafDeptId, leafDeptId)
                        .orderByAsc(SysDeptApplyCompany::getCompanyDeptId))
                .stream()
                .map(SysDeptApplyCompany::getCompanyDeptId)
                .toList();
    }

    private String joinCompanyNames(List<Long> ids, Map<Long, SysDept> map) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .map(id -> map.containsKey(id) ? map.get(id).getDeptName() : "机构#" + id)
                .collect(java.util.stream.Collectors.joining("、"));
    }

    /**
     * 省/市/区县校验：分拨中心、网点必填；其余类型选填，但一旦填写必须完整且层级/父子链正确。
     */
    private void validateRegion(int type, DeptSaveRequest request) {
        boolean required = type == 4 || type == TYPE_OUTLET;
        String province = trim(request.getProvinceCode());
        String city = trim(request.getCityCode());
        String district = trim(request.getDistrictCode());
        boolean any = province != null || city != null || district != null;
        if (!required && !any) {
            return;
        }
        if (province == null || city == null || district == null) {
            throw new BizException(required
                    ? "分拨中心/网点必须选择省、市、区县"
                    : "省、市、区县需同时填写或同时留空");
        }
        List<SysRegion> regions = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .in(SysRegion::getCode, province, city, district));
        Map<String, SysRegion> regionMap = new HashMap<>();
        regions.forEach(item -> regionMap.put(item.getCode(), item));
        SysRegion p = regionMap.get(province);
        SysRegion c = regionMap.get(city);
        SysRegion d = regionMap.get(district);
        if (p == null || !Objects.equals(p.getLevel(), 1)) {
            throw new BizException("省编码不存在或不是省级区划");
        }
        if (c == null || !Objects.equals(c.getLevel(), 2)) {
            throw new BizException("市编码不存在或不是市级区划");
        }
        if (d == null || !Objects.equals(d.getLevel(), 3)) {
            throw new BizException("区县编码不存在或不是区县级区划");
        }
        if (!Objects.equals(c.getParentCode(), province) || !Objects.equals(d.getParentCode(), city)) {
            throw new BizException("省/市/区县不匹配，请重新选择");
        }
        request.setProvinceCode(province);
        request.setCityCode(city);
        request.setDistrictCode(district);
    }

    /**
     * 适用公司校验：仅末端网点需要；为空时分公司管理员默认本公司，总部管理员必须选择。
     */
    private List<Long> validateApplyCompanies(boolean leaf, List<Long> ids, Map<Long, SysDept> map) {
        if (!leaf) {
            return List.of();
        }
        List<Long> cleaned = ids == null ? new ArrayList<>()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (cleaned.isEmpty()) {
            Long companyId = currentCompanyId(map);
            if (companyId == null) {
                throw new BizException("请选择末端网点的适用公司");
            }
            cleaned = List.of(companyId);
        }
        for (Long companyId : cleaned) {
            SysDept company = map.get(companyId);
            if (company == null || !Objects.equals(company.getDeptType(), 2)) {
                throw new BizException("适用公司必须是有效的公司机构");
            }
            if (!Boolean.TRUE.equals(company.getStatus())) {
                throw new BizException("适用公司已禁用：" + company.getDeptName());
            }
        }
        return cleaned;
    }

    /** 当前登录人所属公司：主机构向上最近的公司；总部或无公司祖先返回 null */
    private Long currentCompanyId(Map<Long, SysDept> map) {
        DataScopeContext context = DataScopeContext.current();
        if (context == null || context.getDeptId() == null) {
            return null;
        }
        SysDept current = map.get(context.getDeptId());
        int guard = 0;
        while (current != null && guard++ < 100) {
            if (Objects.equals(current.getDeptType(), 2)) {
                return current.getId();
            }
            if (Objects.equals(current.getDeptType(), TYPE_HQ)) {
                return null;
            }
            current = map.get(current.getParentId());
        }
        return null;
    }

    private void saveApplyCompanies(Long leafDeptId, List<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return;
        }
        Long userId = currentUserId();
        for (Long companyId : companyIds) {
            SysDeptApplyCompany relation = new SysDeptApplyCompany();
            relation.setLeafDeptId(leafDeptId);
            relation.setCompanyDeptId(companyId);
            relation.setCreateBy(userId);
            relation.setCreateTime(LocalDateTime.now());
            applyCompanyMapper.insert(relation);
        }
    }

    private void replaceApplyCompanies(Long leafDeptId, boolean leaf, List<Long> companyIds) {
        applyCompanyMapper.delete(Wrappers.<SysDeptApplyCompany>lambdaQuery()
                .eq(SysDeptApplyCompany::getLeafDeptId, leafDeptId));
        if (leaf) {
            saveApplyCompanies(leafDeptId, companyIds);
        }
    }

    private Long currentUserId() {
        DataScopeContext context = DataScopeContext.current();
        return context == null ? null : context.getUserId();
    }

    /** 批量解析 create_by / update_by 的姓名；sys_user 未建表时返回空 Map，不报错 */
    private Map<Long, String> resolveUserNames(Collection<SysDept> depts) {
        Set<Long> ids = new HashSet<>();
        depts.forEach(item -> {
            if (item.getCreateBy() != null) {
                ids.add(item.getCreateBy());
            }
            if (item.getUpdateBy() != null) {
                ids.add(item.getUpdateBy());
            }
        });
        if (ids.isEmpty() || deptMapper.countTable("sys_user") == 0) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        deptMapper.selectUserNames(ids).forEach(row -> {
            Object id = row.get("id") != null ? row.get("id") : row.get("ID");
            Object realName = row.get("realName") != null ? row.get("realName") : row.get("REALNAME");
            if (id != null) {
                names.put(((Number) id).longValue(), realName == null ? null : realName.toString());
            }
        });
        return names;
    }

    /** 安全取姓名：create_by/update_by 为 null 或 Map 不可变空集时都不会抛 NPE */
    private String nameOf(Map<Long, String> names, Long userId) {
        return userId == null ? null : names.get(userId);
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
                ? LocalDate.parse(text).atStartOfDay()
                : LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private LocalDateTime parseEnd(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 10
                ? LocalDate.parse(text).atTime(23, 59, 59)
                : LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 服务端排序白名单；未识别字段回落到默认 sort_order asc, id asc */
    private Comparator<SysDept> buildComparator(DeptQuery query, Map<Long, SysDept> map,
                                                 Map<Long, String> userNames,
                                                 Map<String, String> regionNames,
                                                 Map<Long, List<Long>> applyCompanies) {
        String by = query.getSortBy();
        Comparator<SysDept> comparator;
        if (by == null) {
            return defaultComparator();
        }
        switch (by) {
            case "deptName" -> comparator = Comparator.comparing(SysDept::getDeptName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "parentName" -> comparator = Comparator.<SysDept, String>comparing(
                    item -> map.containsKey(item.getParentId()) ? map.get(item.getParentId()).getDeptName() : null,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "deptType" -> comparator = Comparator.comparing(SysDept::getDeptType,
                    Comparator.nullsLast(Integer::compareTo));
            case "deptNature" -> comparator = Comparator.comparing(SysDept::getDeptNature,
                    Comparator.nullsLast(Integer::compareTo));
            case "deptCode" -> comparator = Comparator.comparing(SysDept::getDeptCode,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "leader" -> comparator = Comparator.comparing(SysDept::getLeader,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "phone" -> comparator = Comparator.comparing(SysDept::getPhone,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "isLeaf" -> comparator = Comparator.comparing(item -> Boolean.TRUE.equals(item.getIsLeaf()));
            case "status" -> comparator = Comparator.comparing(item -> Boolean.TRUE.equals(item.getStatus()));
            case "regionName" -> comparator = Comparator.<SysDept, String>comparing(
                    item -> regionNameOf(item, regionNames),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "applyCompanyNames" -> comparator = Comparator.<SysDept, String>comparing(
                    item -> joinCompanyNames(applyCompanies.getOrDefault(item.getId(), List.of()), map),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "createTime" -> comparator = Comparator.comparing(SysDept::getCreateTime,
                    Comparator.nullsLast(LocalDateTime::compareTo));
            case "updateTime" -> comparator = Comparator.comparing(SysDept::getUpdateTime,
                    Comparator.nullsLast(LocalDateTime::compareTo));
            case "createByName" -> comparator = Comparator.<SysDept, String>comparing(
                    item -> nameOf(userNames, item.getCreateBy()),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "updateByName" -> comparator = Comparator.<SysDept, String>comparing(
                    item -> nameOf(userNames, item.getUpdateBy()),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default -> {
                return defaultComparator();
            }
        }
        if ("desc".equalsIgnoreCase(query.getSortOrder())) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SysDept::getId);
    }

    private Comparator<SysDept> defaultComparator() {
        return Comparator
                .comparing(SysDept::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysDept::getId);
    }

    /** 完整路径：总部 / 华东公司 / 上海分拨中心 */
    private String buildPath(SysDept dept, Map<Long, SysDept> map) {
        Deque<String> names = new ArrayDeque<>();
        SysDept current = dept;
        int guard = 0;
        while (current != null && guard++ < 100) {
            names.addFirst(current.getDeptName());
            current = current.getParentId() == 0 ? null : map.get(current.getParentId());
        }
        return String.join(" / ", names);
    }

    /** options 只保留选择器需要的字段，避免把授权开关等带到其它模块 */
    private void pruneToOption(List<DeptVO> nodes) {
        nodes.forEach(node -> {
            node.setDeptCode(null);
            node.setAllowShip(null);
            node.setAllowArrival(null);
            node.setAllowPrepaidUnpaid(null);
            node.setAllowCollectUnpaidSign(null);
            node.setLeader(null);
            node.setPhone(null);
            node.setAddress(null);
            node.setSortOrder(null);
            node.setStatus(null);
            node.setRemark(null);
            node.setUpdateTime(null);
            if (node.getChildren() != null) {
                pruneToOption(node.getChildren());
            }
        });
    }

    private void checkMatrix(Integer parentType, Integer childType) {
        if (!isChildAllowed(parentType, childType)) {
            throw new BizException(typeName(parentType) + "下不允许创建" + typeName(childType));
        }
    }

    /** 是否末端是网点专属业务标识，其它类型不允许置 true */
    private void checkLeaf(Integer type, Boolean isLeaf) {
        if (Boolean.TRUE.equals(isLeaf) && !Objects.equals(type, TYPE_OUTLET)) {
            throw new BizException("仅网点可标记为末端机构");
        }
    }

    /** 机构性质必填且只能是 1自营 / 2加盟 / 3合作 */
    private void checkNature(Integer nature) {
        if (nature == null || nature < 1 || nature > 3) {
            throw new BizException("请选择机构性质（自营/加盟/合作）");
        }
    }

    private boolean isChildAllowed(Integer parentType, Integer childType) {
        Set<Integer> allowed = ALLOWED_CHILD.get(parentType);
        return allowed != null && allowed.contains(childType);
    }

    private void checkNameUnique(Map<Long, SysDept> map, Long parentId, String name, Long excludeId) {
        boolean exists = map.values().stream()
                .filter(item -> Objects.equals(item.getParentId(), parentId))
                .filter(item -> !Objects.equals(item.getId(), excludeId))
                .anyMatch(item -> item.getDeptName().equalsIgnoreCase(name));
        if (exists) {
            throw new BizException("同一上级下已存在同名机构：" + name);
        }
    }

    private void checkCodeUnique(Map<Long, SysDept> map, String code, Long excludeId) {
        boolean exists = map.values().stream()
                .filter(item -> !Objects.equals(item.getId(), excludeId))
                .anyMatch(item -> item.getDeptCode().equalsIgnoreCase(code));
        if (exists) {
            throw new BizException("机构编码已存在：" + code);
        }
    }

    /** 节点深度：总部=1 */
    private int depthOf(Long id, Map<Long, SysDept> map) {
        int depth = 0;
        Long current = id;
        while (current != null && current != 0 && depth < 100) {
            depth++;
            SysDept dept = map.get(current);
            if (dept == null) {
                break;
            }
            current = dept.getParentId();
        }
        return depth;
    }

    /** 以该节点为根的子树高度：无子节点=0 */
    private int subtreeHeight(Long id, Map<Long, SysDept> map) {
        int height = 0;
        for (SysDept dept : map.values()) {
            if (Objects.equals(dept.getParentId(), id)) {
                height = Math.max(height, 1 + subtreeHeight(dept.getId(), map));
            }
        }
        return height;
    }

    /** candidateId 是否为 ancestorId 自身或其后代（用于父级环检测） */
    private boolean isDescendant(Long ancestorId, Long candidateId, Map<Long, SysDept> map) {
        Long current = candidateId;
        int guard = 0;
        while (current != null && current != 0 && guard++ < 100) {
            if (Objects.equals(current, ancestorId)) {
                return true;
            }
            SysDept dept = map.get(current);
            if (dept == null) {
                return false;
            }
            current = dept.getParentId();
        }
        return false;
    }

    private String typeName(Integer type) {
        return type == null ? "未知类型" : TYPE_NAME.getOrDefault(type, "未知类型");
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
