package com.yunshu.modules.system.route.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.common.filter.TextFilterMode;
import com.yunshu.modules.system.carrier.entity.SysCarrier;
import com.yunshu.modules.system.carrier.mapper.SysCarrierMapper;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.region.entity.SysRegion;
import com.yunshu.modules.system.region.mapper.SysRegionMapper;
import com.yunshu.modules.system.route.dto.BatchGrantRequest;
import com.yunshu.modules.system.route.dto.RouteQuery;
import com.yunshu.modules.system.route.dto.RouteSaveRequest;
import com.yunshu.modules.system.route.dto.TransferBatchCarrierRequest;
import com.yunshu.modules.system.route.dto.TransferBatchJoinRequest;
import com.yunshu.modules.system.route.dto.TransferBatchStatusRequest;
import com.yunshu.modules.system.route.dto.TransferBatchTypeRequest;
import com.yunshu.modules.system.route.dto.TransferEditRequest;
import com.yunshu.modules.system.route.dto.TransferQuery;
import com.yunshu.modules.system.route.entity.SysRoute;
import com.yunshu.modules.system.route.entity.SysRouteCompany;
import com.yunshu.modules.system.route.entity.SysRouteTransfer;
import com.yunshu.modules.system.route.mapper.SysRouteCompanyMapper;
import com.yunshu.modules.system.route.mapper.SysRouteMapper;
import com.yunshu.modules.system.route.mapper.SysRouteTransferMapper;
import com.yunshu.modules.system.route.service.SysRouteService;
import com.yunshu.modules.system.route.vo.BatchGrantResultVO;
import com.yunshu.modules.system.route.vo.RouteGrantVO;
import com.yunshu.modules.system.route.vo.RegionLibraryPageVO;
import com.yunshu.modules.system.route.vo.RegionLibraryVO;
import com.yunshu.modules.system.route.vo.RouteMatchVO;
import com.yunshu.modules.system.route.vo.RoutePageVO;
import com.yunshu.modules.system.route.vo.RouteSaveResultVO;
import com.yunshu.modules.system.route.vo.RouteTransferVO;
import com.yunshu.modules.system.route.vo.RouteVO;
import com.yunshu.modules.system.route.vo.TransferBatchResultVO;
import com.yunshu.modules.system.route.vo.TransferPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 路由（短驳/干线、外转/直达）+ 授权。
 * 四张路由/承运商表豁免数据权限，所有入口显式按授权关系过滤，禁止裸 selectById 直接返回。
 */
@Service
@RequiredArgsConstructor
public class SysRouteServiceImpl implements SysRouteService {

    private static final int SCOPE_ROUTE = 1;
    private static final int SCOPE_TRANSFER = 2;
    private static final int CATEGORY_SHORT = 1;
    private static final int CATEGORY_TRUNK = 2;
    private static final int TRANSFER_DIRECT = 1;
    private static final int TRANSFER_OUT = 2;
    private static final int MAX_GRANT_BATCH = 50;
    private static final int MAX_MATCH_CANDIDATES = 50;
    private static final String BLOCK_DELETE = "路由被其他公司使用，不能取消，取消授权即可";

    private final SysRouteMapper routeMapper;
    private final SysRouteTransferMapper transferMapper;
    private final SysRouteCompanyMapper companyMapper;
    private final SysDeptMapper deptMapper;
    private final SysRegionMapper regionMapper;
    private final SysCarrierMapper carrierMapper;
    private final UserOrgService userOrgService;

    /* ==================== 短驳/干线 ==================== */

    @Override
    @DS("slave")
    public RoutePageVO page(RouteQuery query) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> granted = authorizedRouteIds(SCOPE_ROUTE, org);
        Map<Long, SysDept> depts = userOrgService.deptMap();
        Map<Long, List<SysRouteCompany>> grants = grantMap(SCOPE_ROUTE);
        Map<Long, String> names = resolveUserNames(routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery())
                .stream().map(SysRoute::getCreateBy).filter(Objects::nonNull).collect(Collectors.toSet()));

        String code = trim(query.getRouteCode());
        String name = trim(query.getRouteName());
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());
        Set<Long> ownerCompanyIds = parseIds(query.getOwnerCompanyIds());
        List<SysRoute> filtered = routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery()).stream()
                .filter(item -> visibleRoute(item, org, granted))
                .filter(item -> TextFilterMode.match(item.getRouteCode(), code,
                        TextFilterMode.modeOf(modes, "routeCode")))
                .filter(item -> TextFilterMode.match(item.getRouteName(), name,
                        TextFilterMode.modeOf(modes, "routeName")))
                .filter(item -> CollectionUtils.isEmpty(query.getRouteCategory())
                        || query.getRouteCategory().contains(item.getRouteCategory()))
                .filter(item -> CollectionUtils.isEmpty(query.getOriginDeptId())
                        || query.getOriginDeptId().contains(item.getOriginDeptId()))
                .filter(item -> CollectionUtils.isEmpty(query.getDestDeptId())
                        || query.getDestDeptId().contains(item.getDestDeptId()))
                .filter(item -> CollectionUtils.isEmpty(query.getStatus())
                        || query.getStatus().contains(item.getStatus()))
                .filter(item -> TextFilterMode.match(item.getWarehouseArea(), trim(query.getWarehouseArea()),
                        TextFilterMode.modeOf(modes, "warehouseArea")))
                .filter(item -> inRange(item.getMileage(), query.getMileageMin(), query.getMileageMax()))
                .filter(item -> inRange(item.getTransitHours(), query.getTransitHoursMin(), query.getTransitHoursMax()))
                .filter(item -> TextFilterMode.match(item.getContactName(), trim(query.getContactName()),
                        TextFilterMode.modeOf(modes, "contactName")))
                .filter(item -> TextFilterMode.match(item.getContactPhone(), trim(query.getContactPhone()),
                        TextFilterMode.modeOf(modes, "contactPhone")))
                .filter(item -> TextFilterMode.match(item.getServiceName(), trim(query.getServiceName()),
                        TextFilterMode.modeOf(modes, "serviceName")))
                .filter(item -> TextFilterMode.match(item.getServicePhone(), trim(query.getServicePhone()),
                        TextFilterMode.modeOf(modes, "servicePhone")))
                .filter(item -> ownerCompanyIds.isEmpty() || ownerCompanyIds.contains(item.getOwnerCompanyId()))
                .filter(item -> grantFilterMatch(query.getGrantScope(), grants.get(item.getId())))
                .sorted(buildRouteComparator(query, depts, grants))
                .toList();

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 10000);
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());

        RoutePageVO result = new RoutePageVO();
        result.setRecords(filtered.subList(from, to).stream()
                .map(item -> toRouteVO(item, depts, grants.get(item.getId()), names, org)).toList());
        result.setTotal(filtered.size());
        return result;
    }

    @Override
    @DS("slave")
    public RouteVO detail(Long id) {
        UserOrgService.UserOrg org = userOrgService.current();
        SysRoute route = requireVisibleRoute(id, org);
        Map<Long, String> names = resolveUserNames(Set.of(route.getCreateBy() == null ? 0L : route.getCreateBy()));
        return toRouteVO(route, userOrgService.deptMap(), grantsOf(SCOPE_ROUTE, id), names, org);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public RouteSaveResultVO create(RouteSaveRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        int category = request.getRouteCategory();
        if (category != CATEGORY_SHORT && category != CATEGORY_TRUNK) {
            throw new BizException("路由类型仅支持短驳或干线");
        }
        SysDept origin = requireRouteDept(request.getOriginDeptId(), "始发网点", scope);
        SysDept dest = requireRouteDept(request.getDestDeptId(), "到达网点", scope);
        if (Objects.equals(origin.getId(), dest.getId())) {
            throw new BizException("始发网点与到达网点不能相同");
        }
        validateTrunkFields(category, request);

        List<SysRoute> sameKey = routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery()
                .eq(SysRoute::getRouteCategory, category)
                .eq(SysRoute::getOriginDeptId, origin.getId())
                .eq(SysRoute::getDestDeptId, dest.getId()));
        if (!sameKey.isEmpty()) {
            SysRoute existing = sameKey.get(0);
            RouteSaveResultVO result = new RouteSaveResultVO();
            result.setId(existing.getId());
            result.setReused(true);
            result.setMessage("该路由已存在，未做修改");
            return result;
        }

        SysRoute route = new SysRoute();
        route.setRouteCode(nextRouteCode(category));
        route.setRouteCategory(category);
        route.setOriginDeptId(origin.getId());
        route.setDestDeptId(dest.getId());
        route.setRouteName(resolveRouteName(request.getRouteName(), category, origin, dest));
        route.setWarehouseArea(trim(request.getWarehouseArea()));
        route.setMileage(request.getMileage());
        route.setTransitHours(request.getTransitHours());
        route.setContactName(trim(request.getContactName()));
        route.setContactPhone(trim(request.getContactPhone()));
        route.setServiceName(trim(request.getServiceName()));
        route.setServicePhone(trim(request.getServicePhone()));
        route.setOwnerCompanyId(org.companyId());
        route.setStatus(request.getStatus() == null || request.getStatus());
        route.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        route.setRemark(trim(request.getRemark()));
        // 豁免表不触发自动填充：dept_id 仅审计锚点，显式写入创建人主机构
        route.setDeptId(org.deptId());
        route.setCreateBy(org.userId());
        route.setUpdateBy(org.userId());
        try {
            routeMapper.insert(route);
        } catch (DuplicateKeyException e) {
            route.setRouteCode(nextRouteCode(category));
            routeMapper.insert(route);
        }

        RouteSaveResultVO result = new RouteSaveResultVO();
        result.setId(route.getId());
        result.setReused(false);
        result.setMessage("创建成功");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, RouteSaveRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        SysRoute existing = requireOwnerRoute(id, org);
        Set<Long> scope = userOrgService.manageDeptIds(org);
        int category = request.getRouteCategory();
        if (category != CATEGORY_SHORT && category != CATEGORY_TRUNK) {
            throw new BizException("路由类型仅支持短驳或干线");
        }
        SysDept origin = requireRouteDept(request.getOriginDeptId(), "始发网点", scope);
        SysDept dest = requireRouteDept(request.getDestDeptId(), "到达网点", scope);
        if (Objects.equals(origin.getId(), dest.getId())) {
            throw new BizException("始发网点与到达网点不能相同");
        }
        validateTrunkFields(category, request);
        List<SysRoute> sameKey = routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery()
                .eq(SysRoute::getRouteCategory, category)
                .eq(SysRoute::getOriginDeptId, origin.getId())
                .eq(SysRoute::getDestDeptId, dest.getId())
                .ne(SysRoute::getId, id));
        if (!sameKey.isEmpty()) {
            throw new BizException("已存在相同路由（类型+始发+到达），不能重复");
        }

        routeMapper.update(null, Wrappers.<SysRoute>lambdaUpdate()
                .eq(SysRoute::getId, id)
                .set(SysRoute::getRouteCategory, category)
                .set(SysRoute::getOriginDeptId, origin.getId())
                .set(SysRoute::getDestDeptId, dest.getId())
                .set(SysRoute::getRouteName, resolveRouteName(request.getRouteName(), category, origin, dest))
                .set(SysRoute::getWarehouseArea, trim(request.getWarehouseArea()))
                .set(SysRoute::getMileage, request.getMileage())
                .set(SysRoute::getTransitHours, request.getTransitHours())
                .set(SysRoute::getContactName, trim(request.getContactName()))
                .set(SysRoute::getContactPhone, trim(request.getContactPhone()))
                .set(SysRoute::getServiceName, trim(request.getServiceName()))
                .set(SysRoute::getServicePhone, trim(request.getServicePhone()))
                .set(SysRoute::getStatus, request.getStatus() == null ? existing.getStatus() : request.getStatus())
                .set(SysRoute::getSortOrder, request.getSortOrder() == null ? 0 : request.getSortOrder())
                .set(SysRoute::getRemark, trim(request.getRemark()))
                .set(SysRoute::getUpdateBy, org.userId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id) {
        UserOrgService.UserOrg org = userOrgService.current();
        requireOwnerRoute(id, org);
        // 短驳/干线路由为网点间物理段，不做跨公司授权；仅清理历史遗留授权行
        companyMapper.delete(Wrappers.<SysRouteCompany>lambdaQuery()
                .eq(SysRouteCompany::getRouteScope, SCOPE_ROUTE)
                .eq(SysRouteCompany::getRouteId, id));
        routeMapper.deleteById(id);
    }

    /* ==================== 外转/直达 ==================== */

    @Override
    @DS("slave")
    public TransferPageVO transferPage(TransferQuery query) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> granted = authorizedRouteIds(SCOPE_TRANSFER, org);
        Map<Long, SysDept> depts = userOrgService.deptMap();
        Map<Long, List<SysRouteCompany>> grants = grantMap(SCOPE_TRANSFER);

        String code = trim(query.getRouteCode());
        String name = trim(query.getRouteName());
        String region = trim(query.getRegionCode());
        String modifyName = trim(query.getUpdateByName());
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());
        Set<Long> ownerCompanyIds = parseIds(query.getOwnerCompanyIds());

        List<SysRouteTransfer> allTransfers = transferMapper.selectList(Wrappers.<SysRouteTransfer>lambdaQuery());
        // 修改人姓名筛选需要先按姓名解析，再由匹配方式决定命中范围
        Map<Long, String> updateNames = modifyName == null ? Map.of()
                : resolveUserNames(allTransfers.stream().map(SysRouteTransfer::getUpdateBy)
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<SysRouteTransfer> candidates = allTransfers.stream()
                .filter(item -> visibleTransfer(item, org, granted))
                .filter(item -> TextFilterMode.match(item.getRouteCode(), code,
                        TextFilterMode.modeOf(modes, "routeCode")))
                .filter(item -> TextFilterMode.match(item.getRouteName(), name,
                        TextFilterMode.modeOf(modes, "routeName")))
                .filter(item -> CollectionUtils.isEmpty(query.getTransferType())
                        || query.getTransferType().contains(item.getTransferType()))
                .filter(item -> CollectionUtils.isEmpty(query.getRegionLevel())
                        || query.getRegionLevel().contains(item.getRegionLevel()))
                .filter(item -> query.getApplyDeptId() == null
                        || Objects.equals(query.getApplyDeptId(), item.getApplyDeptId()))
                .filter(item -> CollectionUtils.isEmpty(query.getCarrierId())
                        || query.getCarrierId().contains(item.getCarrierId()))
                .filter(item -> CollectionUtils.isEmpty(query.getStatus())
                        || query.getStatus().contains(item.getStatus()))
                .filter(item -> ownerCompanyIds.isEmpty() || ownerCompanyIds.contains(item.getOwnerCompanyId()))
                .filter(item -> region == null
                        || TextFilterMode.match(item.getRegionCode(), region,
                        TextFilterMode.modeOf(modes, "regionFullName"))
                        || TextFilterMode.match(item.getRegionFullName(), region,
                        TextFilterMode.modeOf(modes, "regionFullName")))
                .filter(item -> modifyName == null
                        || TextFilterMode.match(updateNames.get(item.getUpdateBy()), modifyName,
                        TextFilterMode.modeOf(modes, "updateByName")))
                .filter(item -> grantScopeMatch(query.getGrantScope(), grants.get(item.getId())))
                .collect(Collectors.toList());

        Set<Long> carrierIds = candidates.stream().map(SysRouteTransfer::getCarrierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysCarrier> carriers = carrierIds.isEmpty() ? Map.of()
                : carrierMapper.selectBatchIds(carrierIds).stream()
                .collect(Collectors.toMap(SysCarrier::getId, item -> item));
        Set<Long> userIds = new HashSet<>();
        candidates.forEach(item -> {
            if (item.getCreateBy() != null) {
                userIds.add(item.getCreateBy());
            }
            if (item.getUpdateBy() != null) {
                userIds.add(item.getUpdateBy());
            }
        });
        Map<Long, String> names = resolveUserNames(userIds);
        List<SysRouteTransfer> visible = candidates.stream()
                .sorted(buildTransferComparator(query, depts, grants, names))
                .toList();

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 10000);
        int from = Math.min((pageNum - 1) * pageSize, visible.size());
        int to = Math.min(from + pageSize, visible.size());

        TransferPageVO result = new TransferPageVO();
        result.setRecords(visible.subList(from, to).stream()
                .map(item -> toTransferVO(item, depts, carriers, grants.get(item.getId()), names, org)).toList());
        result.setTotal(visible.size());
        return result;
    }

    private TransferPageVO emptyTransferPage() {
        TransferPageVO empty = new TransferPageVO();
        empty.setRecords(List.of());
        empty.setTotal(0);
        return empty;
    }

    @Override
    @DS("slave")
    public RouteTransferVO transferDetail(Long id) {
        UserOrgService.UserOrg org = userOrgService.current();
        SysRouteTransfer route = requireVisibleTransfer(id, org);
        SysCarrier carrier = route.getCarrierId() == null ? null : carrierMapper.selectById(route.getCarrierId());
        Map<Long, SysCarrier> carriers = carrier == null ? Map.of() : Map.of(carrier.getId(), carrier);
        return toTransferVO(route, userOrgService.deptMap(), carriers,
                grantsOf(SCOPE_TRANSFER, id),
                resolveUserNames(Set.of(route.getCreateBy() == null ? 0L : route.getCreateBy())), org);
    }

    @Override
    @DS("slave")
    public RegionLibraryPageVO regionLibrary(Long applyDeptId, String keyword, List<Integer> levelList,
                                             List<Boolean> configuredList, String parentCode,
                                             String provinceCode, String cityCode,
                                             String districtKeyword, String townKeyword,
                                             String districtCode, String townCode,
                                             String nameKeyword,
                                             String filterModes, String sortBy, String sortOrder,
                                             long pageNum, long pageSize) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        SysDept applyDept = requireRouteDept(applyDeptId, "配置网点", scope);
        String kw = trim(keyword);
        String parent = trim(parentCode);
        String provincePrefix = trim(provinceCode);
        String cityPrefix = trim(cityCode);
        String districtKw = trim(districtKeyword);
        String townKw = trim(townKeyword);
        String districtPrefix = trim(districtCode);
        String townPrefix = trim(townCode);
        String nameKw = trim(nameKeyword);
        boolean hasLevel = !CollectionUtils.isEmpty(levelList);
        boolean hasConfigured = !CollectionUtils.isEmpty(configuredList);
        if (hasLevel && levelList.stream().anyMatch(item -> item == null || item < 2 || item > 4)) {
            throw new BizException("区划级别仅支持市、区县、乡级");
        }
        Map<String, TextFilterMode> modes = TextFilterMode.parse(filterModes);
        boolean wantConfigured = configuredList != null && configuredList.contains(Boolean.TRUE);
        boolean wantUnconfigured = configuredList != null && configuredList.contains(Boolean.FALSE);

        // 当前网点下本公司可见的路由 -> 区划配置状态；配置筛选必须在分页前落到 SQL，否则总数不准
        Set<Long> granted = authorizedRouteIds(SCOPE_TRANSFER, org);
        Map<String, SysRouteTransfer> configuredRoutes = transferMapper.selectList(
                        Wrappers.<SysRouteTransfer>lambdaQuery()
                                .eq(SysRouteTransfer::getApplyDeptId, applyDept.getId()))
                .stream()
                .filter(item -> visibleTransfer(item, org, granted))
                .collect(Collectors.toMap(SysRouteTransfer::getRegionCode, item -> item, (a, b) -> a));
        Set<String> configuredCodes = configuredRoutes.keySet();

        RegionLibraryPageVO result = new RegionLibraryPageVO();
        if (wantConfigured && !wantUnconfigured && configuredCodes.isEmpty()) {
            result.setRecords(List.of());
            result.setTotal(0);
            return result;
        }

        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(200, Math.max(1, pageSize));
        LambdaQueryWrapper<SysRegion> wrapper = Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getStatus, 1)
                .ge(SysRegion::getLevel, 2)
                .le(SysRegion::getLevel, 4)
                .in(hasLevel, SysRegion::getLevel, hasLevel ? levelList : List.of())
                .eq(parent != null, SysRegion::getParentCode, parent)
                .likeRight(provincePrefix != null && provincePrefix.length() >= 2,
                        SysRegion::getCode, provincePrefix == null ? "" : provincePrefix.substring(0, 2))
                .likeRight(cityPrefix != null && cityPrefix.length() >= 4,
                        SysRegion::getCode, cityPrefix == null ? "" : cityPrefix.substring(0, 4))
                .likeRight(districtPrefix != null && districtPrefix.length() >= 6,
                        SysRegion::getCode, districtPrefix == null ? "" : districtPrefix.substring(0, 6))
                .likeRight(townPrefix != null, SysRegion::getCode, townPrefix == null ? "" : townPrefix)
                // 关键词按名称模糊命中；命中多级时只取最高一级（如“山东”命中省与同名乡镇，只按山东省展开），
                // 再按该级别编码前缀带出全部下级（省2位/市4位/区县6位/乡级全码），条件落在 SQL 层保证分页总数准确
                .apply(kw != null,
                        "EXISTS (SELECT 1 FROM sys_region kwr WHERE kwr.status = 1 AND kwr.name LIKE {0} "
                                + "AND kwr.level = (SELECT MIN(k2.level) FROM sys_region k2 "
                                + "WHERE k2.status = 1 AND k2.name LIKE {1}) "
                                + "AND sys_region.code LIKE CONCAT(CASE kwr.level "
                                + "WHEN 1 THEN LEFT(kwr.code, 2) WHEN 2 THEN LEFT(kwr.code, 4) "
                                + "WHEN 3 THEN LEFT(kwr.code, 6) ELSE kwr.code END, '%'))",
                        "%" + kw + "%", "%" + kw + "%");
        applyRegionTextCondition(wrapper, SysRegion::getFullName, districtKw,
                TextFilterMode.modeOf(modes, "districtKeyword"));
        applyRegionTextCondition(wrapper, SysRegion::getName, townKw,
                TextFilterMode.modeOf(modes, "townKeyword"));
        // 区划名称：任意级别按名称/全称匹配，与级别/省/市/区县/乡级/配置可叠加
        applyRegionNameCondition(wrapper, nameKw, TextFilterMode.modeOf(modes, "nameKeyword"));
        // 服务端排序：仅开放 level/name，其他字段忽略并回落编码升序
        boolean desc = "desc".equalsIgnoreCase(trim(sortOrder));
        String sortField = trim(sortBy);
        if ("level".equals(sortField)) {
            wrapper.orderBy(true, !desc, SysRegion::getLevel);
        } else if ("name".equals(sortField)) {
            wrapper.orderBy(true, !desc, SysRegion::getName);
        }
        wrapper.orderByAsc(SysRegion::getCode);
        if (wantConfigured && !wantUnconfigured) {
            wrapper.in(SysRegion::getCode, configuredCodes);
        } else if (!wantConfigured && wantUnconfigured && !configuredCodes.isEmpty()) {
            wrapper.notIn(SysRegion::getCode, configuredCodes);
        }

        Page<SysRegion> page = regionMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        List<SysRegion> regions = page.getRecords();
        result.setTotal(page.getTotal());
        if (regions.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        Set<String> ancestorCodes = new HashSet<>();
        Map<String, String[]> ancestorCodeMap = new HashMap<>();
        for (SysRegion region : regions) {
            String code = region.getCode();
            String province = code.length() >= 2 ? code.substring(0, 2) + "0000" : null;
            String city = code.length() >= 4 ? code.substring(0, 4) + "00" : null;
            String district = code.length() >= 6 ? code.substring(0, 6) : null;
            ancestorCodeMap.put(code, new String[]{province, city, district});
            if (province != null) {
                ancestorCodes.add(province);
            }
            if (city != null) {
                ancestorCodes.add(city);
            }
            if (district != null) {
                ancestorCodes.add(district);
            }
        }
        Map<String, String> ancestorNames = regionMapper.selectBatchIds(ancestorCodes).stream()
                .collect(Collectors.toMap(SysRegion::getCode, SysRegion::getName));

        List<RegionLibraryVO> records = new ArrayList<>();
        for (SysRegion region : regions) {
            SysRouteTransfer route = configuredRoutes.get(region.getCode());
            String[] ancestors = ancestorCodeMap.get(region.getCode());
            RegionLibraryVO vo = new RegionLibraryVO();
            vo.setCode(region.getCode());
            vo.setName(region.getName());
            vo.setLevel(region.getLevel());
            vo.setParentCode(region.getParentCode());
            vo.setFullName(regionFullName(region));
            vo.setProvinceName(ancestors[0] == null ? null : ancestorNames.get(ancestors[0]));
            vo.setCityName(ancestors[1] == null ? null : ancestorNames.get(ancestors[1]));
            vo.setDistrictName(ancestors[2] == null ? null : ancestorNames.get(ancestors[2]));
            vo.setConfigured(route != null);
            vo.setRouteId(route == null ? null : route.getId());
            vo.setTransferType(route == null ? null : route.getTransferType());
            records.add(vo);
        }
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TransferBatchResultVO batchJoin(TransferBatchJoinRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        SysDept applyDept = requireRouteDept(request.getApplyDeptId(), "配置网点", scope);
        List<String> codes = request.getRegionCodes().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        Map<String, SysRegion> regionMap = regionMapper.selectBatchIds(codes).stream()
                .collect(Collectors.toMap(SysRegion::getCode, item -> item));

        TransferBatchResultVO result = new TransferBatchResultVO();
        result.setTotal(codes.size());
        for (String code : codes) {
            SysRegion region = regionMap.get(code);
            if (region == null || !Objects.equals(1, region.getStatus())) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(code + "：区划不存在或已停用");
                continue;
            }
            if (region.getLevel() == null || region.getLevel() < 2 || region.getLevel() > 4) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(code + "：仅支持市、区县、乡级区划");
                continue;
            }
            // 唯一口径：适用网点 + 目的地（对齐 luohuo，类型为路由属性而非键的一部分）
            List<SysRouteTransfer> sameKey = transferMapper.selectList(Wrappers.<SysRouteTransfer>lambdaQuery()
                    .eq(SysRouteTransfer::getApplyDeptId, applyDept.getId())
                    .eq(SysRouteTransfer::getRegionCode, region.getCode())
                    .last("LIMIT 1"));
            if (!sameKey.isEmpty()) {
                SysRouteTransfer existing = sameKey.get(0);
                if (Objects.equals(existing.getOwnerCompanyId(), org.companyId())) {
                    // D5：本公司创建的路由可覆盖公共字段
                    transferMapper.update(null, Wrappers.<SysRouteTransfer>lambdaUpdate()
                            .eq(SysRouteTransfer::getId, existing.getId())
                            .set(SysRouteTransfer::getRouteName, regionFullName(region))
                            .set(SysRouteTransfer::getRegionLevel, region.getLevel())
                            .set(SysRouteTransfer::getRegionFullName, regionFullName(region))
                            .set(SysRouteTransfer::getUpdateBy, org.userId()));
                    ensureGrant(SCOPE_TRANSFER, existing.getId(), org);
                    result.setOverwritten(result.getOverwritten() + 1);
                    result.addDetail(code + "：已有本公司路由，已覆盖公共字段");
                } else {
                    // D5：他公司创建的路由只补授权，绝不覆盖数据
                    boolean added = ensureGrant(SCOPE_TRANSFER, existing.getId(), org);
                    if (added) {
                        result.setGranted(result.getGranted() + 1);
                        result.addDetail(code + "：路由由他公司创建，仅补授权未覆盖");
                    } else {
                        result.setSkipped(result.getSkipped() + 1);
                        result.addDetail(code + "：已存在且本公司已有授权，跳过");
                    }
                }
                continue;
            }

            String routeCode = transferCode(region.getCode(), TRANSFER_OUT);
            assertCodeFree(applyDept.getId(), routeCode);
            SysRouteTransfer deleted = transferMapper.selectDeletedByKey(applyDept.getId(), region.getCode(),
                    TRANSFER_OUT);
            if (deleted != null) {
                transferMapper.restoreById(deleted.getId(), routeCode, regionFullName(region), region.getLevel(),
                        regionFullName(region), null, org.companyId(), 0, org.deptId(), org.userId());
                ensureGrant(SCOPE_TRANSFER, deleted.getId(), org);
                result.setRestored(result.getRestored() + 1);
                result.addDetail(code + "：恢复已删除路由");
                continue;
            }

            SysRouteTransfer route = new SysRouteTransfer();
            route.setRouteCode(routeCode);
            route.setRouteName(regionFullName(region));
            route.setTransferType(TRANSFER_OUT);
            // apply_dept_id 存业务适用网点；dept_id 仅权限锚点，两者禁止混用
            route.setApplyDeptId(applyDept.getId());
            route.setRegionCode(region.getCode());
            route.setRegionLevel(region.getLevel());
            route.setRegionFullName(regionFullName(region));
            route.setCarrierId(null);
            route.setOwnerCompanyId(org.companyId());
            route.setStatus(true);
            route.setSortOrder(0);
            route.setDeptId(org.deptId());
            route.setCreateBy(org.userId());
            route.setUpdateBy(org.userId());
            transferMapper.insert(route);
            ensureGrant(SCOPE_TRANSFER, route.getId(), org);
            result.setCreated(result.getCreated() + 1);
            result.addDetail(code + "：新建外转路由");
        }
        result.setMessage("新增 " + result.getCreated() + " 条，恢复 " + result.getRestored()
                + " 条，覆盖 " + result.getOverwritten() + " 条，补授权 " + result.getGranted()
                + " 条，跳过 " + result.getSkipped() + " 条");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void updateTransfer(Long id, TransferEditRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        SysRouteTransfer existing = requireOwnerTransfer(id, org);
        Set<Long> scope = userOrgService.manageDeptIds(org);
        // 目的地、编码、类型不允许在编辑接口变更；承运商仅外转可挂，直达强制清空
        Long carrierId = resolveCarrierId(existing.getTransferType(), request.getCarrierId(), scope);
        String routeName = StringUtils.hasText(request.getRouteName())
                ? request.getRouteName().trim()
                : (StringUtils.hasText(existing.getRegionFullName()) ? existing.getRegionFullName() : existing.getRouteName());

        transferMapper.update(null, Wrappers.<SysRouteTransfer>lambdaUpdate()
                .eq(SysRouteTransfer::getId, id)
                .set(SysRouteTransfer::getRouteName, routeName)
                .set(SysRouteTransfer::getCarrierId, carrierId)
                .set(SysRouteTransfer::getStatus,
                        request.getStatus() == null ? existing.getStatus() : request.getStatus())
                .set(SysRouteTransfer::getSortOrder,
                        request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder())
                .set(SysRouteTransfer::getRemark, trim(request.getRemark()))
                .set(SysRouteTransfer::getUpdateBy, org.userId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TransferBatchResultVO switchTransferType(TransferBatchTypeRequest request) {
        int type = request.getTransferType();
        if (type != TRANSFER_DIRECT && type != TRANSFER_OUT) {
            throw new BizException("路由类型仅支持直达或外转");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).distinct().toList();
        TransferBatchResultVO result = new TransferBatchResultVO();
        result.setTotal(ids.size());
        int changed = 0;
        for (Long id : ids) {
            SysRouteTransfer route;
            try {
                route = requireOwnerTransfer(id, org);
            } catch (BizException e) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(id + "：" + e.getMessage());
                continue;
            }
            if (Objects.equals(type, route.getTransferType())) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            boolean conflict = transferMapper.selectCount(Wrappers.<SysRouteTransfer>lambdaQuery()
                    .eq(SysRouteTransfer::getApplyDeptId, route.getApplyDeptId())
                    .eq(SysRouteTransfer::getRegionCode, route.getRegionCode())
                    .ne(SysRouteTransfer::getId, id)) > 0;
            if (conflict) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(route.getRouteCode() + "：同网点该目的地已有路由，无法切换");
                continue;
            }
            transferMapper.update(null, Wrappers.<SysRouteTransfer>lambdaUpdate()
                    .eq(SysRouteTransfer::getId, id)
                    .set(SysRouteTransfer::getTransferType, type)
                    .set(SysRouteTransfer::getRouteCode, transferCode(route.getRegionCode(), type))
                    .set(SysRouteTransfer::getCarrierId, type == TRANSFER_DIRECT ? null : route.getCarrierId())
                    .set(SysRouteTransfer::getUpdateBy, org.userId()));
            changed++;
        }
        result.setOverwritten(changed);
        result.setMessage("切换成功 " + changed + " 条，跳过 " + result.getSkipped() + " 条");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TransferBatchResultVO batchCarrier(TransferBatchCarrierRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        Long carrierId = resolveCarrierId(TRANSFER_OUT, request.getCarrierId(), scope);
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).distinct().toList();
        TransferBatchResultVO result = new TransferBatchResultVO();
        result.setTotal(ids.size());
        int changed = 0;
        for (Long id : ids) {
            SysRouteTransfer route;
            try {
                route = requireOwnerTransfer(id, org);
            } catch (BizException e) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(id + "：" + e.getMessage());
                continue;
            }
            if (Objects.equals(TRANSFER_DIRECT, route.getTransferType())) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(route.getRouteCode() + "：直达路由不能挂承运商");
                continue;
            }
            transferMapper.update(null, Wrappers.<SysRouteTransfer>lambdaUpdate()
                    .eq(SysRouteTransfer::getId, id)
                    .set(SysRouteTransfer::getCarrierId, carrierId)
                    .set(SysRouteTransfer::getUpdateBy, org.userId()));
            changed++;
        }
        result.setOverwritten(changed);
        result.setMessage("挂载成功 " + changed + " 条，跳过 " + result.getSkipped() + " 条");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TransferBatchResultVO batchStatus(TransferBatchStatusRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).distinct().toList();
        TransferBatchResultVO result = new TransferBatchResultVO();
        result.setTotal(ids.size());
        int changed = 0;
        for (Long id : ids) {
            try {
                requireOwnerTransfer(id, org);
            } catch (BizException e) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail(id + "：" + e.getMessage());
                continue;
            }
            transferMapper.update(null, Wrappers.<SysRouteTransfer>lambdaUpdate()
                    .eq(SysRouteTransfer::getId, id)
                    .set(SysRouteTransfer::getStatus, request.getStatus())
                    .set(SysRouteTransfer::getUpdateBy, org.userId()));
            changed++;
        }
        result.setOverwritten(changed);
        result.setMessage((Boolean.TRUE.equals(request.getStatus()) ? "启用" : "停用")
                + "成功 " + changed + " 条，跳过 " + result.getSkipped() + " 条");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void deleteTransfer(Long id) {
        UserOrgService.UserOrg org = userOrgService.current();
        SysRouteTransfer route = id == null ? null : transferMapper.selectById(id);
        if (route == null) {
            throw new BizException("路由不存在或已删除");
        }
        if (!org.hq() && !Objects.equals(route.getOwnerCompanyId(), org.companyId())) {
            // 非创建公司：有授权说明该路由正被本公司使用，按“使用中”拦截；无授权则直接无权
            if (existsGrant(SCOPE_TRANSFER, id, org.companyId())) {
                throw new BizException(BLOCK_DELETE);
            }
            throw new BizException(403, "只有创建公司可以操作该路由");
        }
        List<SysRouteCompany> grants = grantsOf(SCOPE_TRANSFER, id);
        // 0 号行为总部自身的“全部分公司”授权，不算他公司在用
        boolean otherUsing = org.hq()
                ? grants.stream().anyMatch(item -> !Objects.equals(0L, item.getCompanyDeptId()))
                : grants.stream().anyMatch(item -> !Objects.equals(item.getCompanyDeptId(), org.companyId()));
        if (otherUsing) {
            throw new BizException(BLOCK_DELETE);
        }
        companyMapper.delete(Wrappers.<SysRouteCompany>lambdaQuery()
                .eq(SysRouteCompany::getRouteScope, SCOPE_TRANSFER)
                .eq(SysRouteCompany::getRouteId, id));
        transferMapper.deleteById(id);
    }

    /* ==================== 授权 ==================== */

    @Override
    @DS("slave")
    public List<RouteGrantVO> grants(Integer routeScope, Long routeId) {
        int scope = requireGrantableScope(routeScope);
        UserOrgService.UserOrg org = userOrgService.current();
        requireRouteExists(scope, routeId, org);
        Map<Long, SysDept> depts = userOrgService.deptMap();
        List<SysRouteCompany> rows = grantsOf(scope, routeId).stream()
                .filter(item -> org.hq() || Objects.equals(item.getCompanyDeptId(), org.companyId()))
                .toList();
        Set<Long> userIds = rows.stream().map(SysRouteCompany::getGrantBy)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = resolveUserNames(userIds);
        return rows.stream().map(item -> {
            RouteGrantVO vo = new RouteGrantVO();
            vo.setId(item.getId());
            vo.setCompanyDeptId(item.getCompanyDeptId());
            SysDept dept = depts.get(item.getCompanyDeptId());
            vo.setCompanyName(Objects.equals(0L, item.getCompanyDeptId())
                    ? "全部分公司"
                    : (dept == null ? null : dept.getDeptName()));
            vo.setGrantSource(item.getGrantSource());
            vo.setGrantSourceName(grantSourceName(item.getGrantSource()));
            vo.setGrantBy(item.getGrantBy());
            vo.setGrantByName(names.get(item.getGrantBy()));
            vo.setGrantTime(item.getGrantTime());
            vo.setRemark(item.getRemark());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public int grant(Integer routeScope, Long routeId, List<Long> companyDeptIds) {
        int scope = requireGrantableScope(routeScope);
        UserOrgService.UserOrg org = userOrgService.current();
        if (!org.hq()) {
            throw new BizException(403, "仅总部可手动授权");
        }
        requireRouteExists(scope, routeId, org);
        if (existsGrant(scope, routeId, 0L)) {
            throw new BizException("该路由已授权全部分公司，无需逐家授权");
        }
        List<Long> companyIds = validateCompanies(companyDeptIds);
        Set<Long> existing = grantsOf(scope, routeId).stream()
                .map(SysRouteCompany::getCompanyDeptId).collect(Collectors.toSet());
        int added = 0;
        for (Long companyId : companyIds) {
            if (existing.contains(companyId)) {
                continue;
            }
            SysRouteCompany row = new SysRouteCompany();
            row.setRouteScope(scope);
            row.setRouteId(routeId);
            row.setCompanyDeptId(companyId);
            row.setGrantSource(2);
            row.setGrantBy(org.userId());
            row.setGrantTime(LocalDateTime.now());
            row.setRemark("手动授权");
            try {
                companyMapper.insert(row);
                added++;
            } catch (DuplicateKeyException e) {
                // 并发重复授权按幂等处理
            }
        }
        return added;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void revoke(Integer routeScope, Long routeId, Long companyDeptId) {
        int scope = requireGrantableScope(routeScope);
        UserOrgService.UserOrg org = userOrgService.current();
        if (!org.hq() && !Objects.equals(companyDeptId, org.companyId())) {
            throw new BizException(403, "只能取消本公司的授权");
        }
        requireRouteExists(scope, routeId, org);
        SysRouteCompany row = companyMapper.selectOne(Wrappers.<SysRouteCompany>lambdaQuery()
                .eq(SysRouteCompany::getRouteScope, scope)
                .eq(SysRouteCompany::getRouteId, routeId)
                .eq(SysRouteCompany::getCompanyDeptId, companyDeptId)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BizException("授权记录不存在");
        }
        companyMapper.deleteById(row.getId());
        if (!existsAnyGrant(scope, routeId)) {
            // 删至无授权：路由自动停用
            if (scope == SCOPE_ROUTE) {
                routeMapper.update(null, Wrappers.<SysRoute>lambdaUpdate()
                        .eq(SysRoute::getId, routeId).set(SysRoute::getStatus, false));
            } else {
                transferMapper.update(null, Wrappers.<SysRouteTransfer>lambdaUpdate()
                        .eq(SysRouteTransfer::getId, routeId).set(SysRouteTransfer::getStatus, false));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public BatchGrantResultVO batchGrant(BatchGrantRequest request) {
        int scope = requireGrantableScope(request.getRouteScope());
        UserOrgService.UserOrg org = userOrgService.current();
        if (!org.hq()) {
            throw new BizException(403, "仅总部可批量授权");
        }
        List<Long> routeIds = request.getRouteIds().stream().filter(Objects::nonNull).distinct().toList();
        List<Long> companyIds = validateCompanies(request.getCompanyDeptIds());
        if (routeIds.size() > MAX_GRANT_BATCH || companyIds.size() > MAX_GRANT_BATCH) {
            throw new BizException("单次批量授权上限为 50 条路由 × 50 家公司");
        }
        int success = 0;
        int skipped = 0;
        for (Long routeId : routeIds) {
            requireRouteExists(scope, routeId, org);
            boolean all = existsGrant(scope, routeId, 0L);
            Set<Long> existing = grantsOf(scope, routeId).stream()
                    .map(SysRouteCompany::getCompanyDeptId).collect(Collectors.toSet());
            for (Long companyId : companyIds) {
                if (all || existing.contains(companyId)) {
                    skipped++;
                    continue;
                }
                SysRouteCompany row = new SysRouteCompany();
                row.setRouteScope(scope);
                row.setRouteId(routeId);
                row.setCompanyDeptId(companyId);
                row.setGrantSource(2);
                row.setGrantBy(org.userId());
                row.setGrantTime(LocalDateTime.now());
                row.setRemark("批量授权");
                try {
                    companyMapper.insert(row);
                    success++;
                } catch (DuplicateKeyException e) {
                    skipped++;
                }
            }
        }
        BatchGrantResultVO result = new BatchGrantResultVO();
        result.setSuccess(success);
        result.setSkipped(skipped);
        result.setMessage("成功 " + success + " 条，跳过 " + skipped + " 条");
        return result;
    }

    /* ==================== 开单匹配 ==================== */

    @Override
    @DS("slave")
    public RouteMatchVO match(String destRegionCode, Long destDeptId, Long originDeptId, Long applyDeptId) {
        if (!StringUtils.hasText(destRegionCode) && destDeptId == null) {
            throw new BizException("请提供目的地（区划编码或网点）");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        Map<Long, SysDept> depts = userOrgService.deptMap();
        Map<String, Integer> regionDepths = regionDepths(destRegionCode, destDeptId);
        Set<Long> destDeptIds = resolveDestDeptIds(destDeptId, regionDepths.keySet(), depts);
        Set<Long> grantedTransfer = authorizedRouteIds(SCOPE_TRANSFER, org);
        Set<Long> grantedRoute = authorizedRouteIds(SCOPE_ROUTE, org);

        List<Cand> candidates = new ArrayList<>();

        // 1) 直达 / 外转
        for (SysRouteTransfer item : transferMapper.selectList(Wrappers.<SysRouteTransfer>lambdaQuery()
                .eq(SysRouteTransfer::getStatus, true))) {
            if (!visibleTransfer(item, org, grantedTransfer)) {
                continue;
            }
            if (applyDeptId != null && !Objects.equals(applyDeptId, item.getApplyDeptId())) {
                continue;
            }
            Integer depth = regionDepths.get(item.getRegionCode());
            if (depth == null) {
                continue;
            }
            int rank = Objects.equals(TRANSFER_DIRECT, item.getTransferType()) ? 0 : 2;
            SysCarrier carrier = item.getCarrierId() == null ? null : carrierMapper.selectById(item.getCarrierId());
            RouteMatchVO.RouteMatchNodeVO node = new RouteMatchVO.RouteMatchNodeVO();
            node.setRouteScope(SCOPE_TRANSFER);
            node.setRouteId(item.getId());
            node.setRouteCode(item.getRouteCode());
            node.setCategory(item.getTransferType() == TRANSFER_DIRECT ? 3 : 4);
            node.setRouteName(item.getRouteName());
            node.setFromDeptId(item.getApplyDeptId());
            node.setFromDeptName(deptName(depts, item.getApplyDeptId()));
            node.setToDeptId(null);
            node.setToDeptName(item.getRegionFullName());
            node.setTransitHours(null);
            node.setMileage(null);
            node.setCarrierId(item.getCarrierId());
            node.setCarrierName(carrier == null ? null : carrier.getCarrierName());
            candidates.add(new Cand(rank, null, null, 1, item.getSortOrder() == null ? 0 : item.getSortOrder(),
                    item.getId(), List.of(node)));
        }

        // 2) 网络内完整链路（短驳/干线）
        List<SysRoute> routes = routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery()
                .eq(SysRoute::getStatus, true))
                .stream().filter(item -> visibleRoute(item, org, grantedRoute)).toList();
        if (!destDeptIds.isEmpty() && !routes.isEmpty()) {
            Map<Long, List<SysRoute>> adjacency = new HashMap<>();
            routes.forEach(item -> adjacency
                    .computeIfAbsent(item.getOriginDeptId(), key -> new ArrayList<>()).add(item));
            if (originDeptId != null) {
                List<List<SysRoute>> chains = new ArrayList<>();
                walkChains(originDeptId, adjacency, destDeptIds, new ArrayList<>(), new HashSet<>(Set.of(originDeptId)), chains);
                chains.forEach(chain -> candidates.add(toNetworkCand(chain, depts)));
            } else {
                for (SysRoute item : routes) {
                    if (destDeptIds.contains(item.getDestDeptId())) {
                        candidates.add(toNetworkCand(List.of(item), depts));
                    }
                }
            }
        }

        candidates.sort(Comparator
                .comparingInt(Cand::rank)
                .thenComparingDouble(item -> item.transit() == null ? Double.MAX_VALUE : item.transit().doubleValue())
                .thenComparingDouble(item -> item.mileage() == null ? Double.MAX_VALUE : item.mileage().doubleValue())
                .thenComparingInt(Cand::hops)
                .thenComparingInt(Cand::sortOrder)
                .thenComparingLong(Cand::id));

        List<Cand> limited = candidates.stream().limit(MAX_MATCH_CANDIDATES).toList();
        RouteMatchVO result = new RouteMatchVO();
        result.setMatched(!limited.isEmpty());
        if (limited.isEmpty()) {
            result.setMessage("当前公司未授权到该目的地的路由，请联系总部授权或新增路由");
            result.setRecords(List.of());
            return result;
        }
        List<RouteMatchVO.RouteMatchItemVO> records = new ArrayList<>();
        for (int i = 0; i < limited.size(); i++) {
            Cand cand = limited.get(i);
            RouteMatchVO.RouteMatchItemVO item = new RouteMatchVO.RouteMatchItemVO();
            item.setCategoryRank(cand.rank());
            item.setCategoryLabel(cand.rank() == 0 ? "直达" : cand.rank() == 1 ? "网络链路" : "外转");
            item.setHops(cand.hops());
            item.setTotalTransitHours(cand.transit());
            item.setTotalMileage(cand.mileage());
            item.setRecommended(i == 0);
            item.setNodes(cand.nodes());
            records.add(item);
        }
        result.setRecommendedId(limited.get(0).id());
        result.setRecords(records);
        result.setMessage("匹配到 " + limited.size() + " 条路由");
        return result;
    }

    /* ==================== 内部：可见性/授权 ==================== */

    private Set<Long> authorizedRouteIds(int scope, UserOrgService.UserOrg org) {
        if (org.hq()) {
            return null;
        }
        return companyMapper.selectList(Wrappers.<SysRouteCompany>lambdaQuery()
                        .eq(SysRouteCompany::getRouteScope, scope)
                        .in(SysRouteCompany::getCompanyDeptId, List.of(org.companyId(), 0L)))
                .stream().map(SysRouteCompany::getRouteId).collect(Collectors.toSet());
    }

    private boolean visibleRoute(SysRoute route, UserOrgService.UserOrg org, Set<Long> granted) {
        // 短驳/干线为网点间物理段，只按创建公司归属可见，不做跨公司授权
        return org.hq() || Objects.equals(route.getOwnerCompanyId(), org.companyId());
    }

    private boolean visibleTransfer(SysRouteTransfer route, UserOrgService.UserOrg org, Set<Long> granted) {
        if (org.hq()) {
            return true;
        }
        return Objects.equals(route.getOwnerCompanyId(), org.companyId())
                || (granted != null && granted.contains(route.getId()));
    }

    private SysRoute requireVisibleRoute(Long id, UserOrgService.UserOrg org) {
        SysRoute route = id == null ? null : routeMapper.selectById(id);
        if (route == null) {
            throw new BizException(403, "无权访问该路由");
        }
        if (!org.hq() && !Objects.equals(route.getOwnerCompanyId(), org.companyId())) {
            throw new BizException(403, "无权访问该路由");
        }
        return route;
    }

    private SysRouteTransfer requireVisibleTransfer(Long id, UserOrgService.UserOrg org) {
        SysRouteTransfer route = id == null ? null : transferMapper.selectById(id);
        if (route == null) {
            throw new BizException(403, "无权访问该路由");
        }
        if (!org.hq() && !Objects.equals(route.getOwnerCompanyId(), org.companyId())
                && !existsGrant(SCOPE_TRANSFER, id, org.companyId())
                && !existsGrant(SCOPE_TRANSFER, id, 0L)) {
            throw new BizException(403, "无权访问该路由");
        }
        return route;
    }

    private SysRoute requireOwnerRoute(Long id, UserOrgService.UserOrg org) {
        SysRoute route = id == null ? null : routeMapper.selectById(id);
        if (route == null) {
            throw new BizException("路由不存在或已删除");
        }
        if (!org.hq() && !Objects.equals(route.getOwnerCompanyId(), org.companyId())) {
            throw new BizException(403, "只有创建公司可以操作该路由");
        }
        return route;
    }

    private SysRouteTransfer requireOwnerTransfer(Long id, UserOrgService.UserOrg org) {
        SysRouteTransfer route = id == null ? null : transferMapper.selectById(id);
        if (route == null) {
            throw new BizException("路由不存在或已删除");
        }
        if (!org.hq() && !Objects.equals(route.getOwnerCompanyId(), org.companyId())) {
            throw new BizException(403, "只有创建公司可以操作该路由");
        }
        return route;
    }

    private void requireRouteExists(int scope, Long routeId, UserOrgService.UserOrg org) {
        if (scope == SCOPE_ROUTE) {
            requireVisibleRoute(routeId, org);
        } else {
            requireVisibleTransfer(routeId, org);
        }
    }

    private List<SysRouteCompany> grantsOf(int scope, Long routeId) {
        return companyMapper.selectList(Wrappers.<SysRouteCompany>lambdaQuery()
                .eq(SysRouteCompany::getRouteScope, scope)
                .eq(SysRouteCompany::getRouteId, routeId)
                .orderByAsc(SysRouteCompany::getId));
    }

    private Map<Long, List<SysRouteCompany>> grantMap(int scope) {
        return companyMapper.selectList(Wrappers.<SysRouteCompany>lambdaQuery()
                        .eq(SysRouteCompany::getRouteScope, scope))
                .stream().collect(Collectors.groupingBy(SysRouteCompany::getRouteId));
    }

    private boolean existsGrant(int scope, Long routeId, Long companyDeptId) {
        return companyMapper.selectCount(Wrappers.<SysRouteCompany>lambdaQuery()
                .eq(SysRouteCompany::getRouteScope, scope)
                .eq(SysRouteCompany::getRouteId, routeId)
                .eq(SysRouteCompany::getCompanyDeptId, companyDeptId)) > 0;
    }

    private boolean existsAnyGrant(int scope, Long routeId) {
        return companyMapper.selectCount(Wrappers.<SysRouteCompany>lambdaQuery()
                .eq(SysRouteCompany::getRouteScope, scope)
                .eq(SysRouteCompany::getRouteId, routeId)) > 0;
    }

    /** 创建/同键复用：总部补 0 号行；分公司补本公司行；已存在则跳过 */
    private boolean ensureGrant(int scope, Long routeId, UserOrgService.UserOrg org) {
        if (org.hq()) {
            if (existsGrant(scope, routeId, 0L)) {
                return false;
            }
            insertGrant(scope, routeId, 0L, 3, org.userId(), "创建时授权全部分公司（动态，含后续新增）");
            return true;
        }
        if (existsGrant(scope, routeId, 0L) || existsGrant(scope, routeId, org.companyId())) {
            return false;
        }
        insertGrant(scope, routeId, org.companyId(), 1, org.userId(), "创建自动授权本公司");
        return true;
    }

    private void insertGrant(int scope, Long routeId, Long companyDeptId, int source, Long userId, String remark) {
        SysRouteCompany row = new SysRouteCompany();
        row.setRouteScope(scope);
        row.setRouteId(routeId);
        row.setCompanyDeptId(companyDeptId);
        row.setGrantSource(source);
        row.setGrantBy(userId);
        row.setGrantTime(LocalDateTime.now());
        row.setRemark(remark);
        companyMapper.insert(row);
    }

    /** 区划库两列需要把匹配方式落到 SQL，避免先分页再过滤导致总数不准 */
    private void applyRegionTextCondition(LambdaQueryWrapper<SysRegion> wrapper,
                                          SFunction<SysRegion, String> column,
                                          String keyword, TextFilterMode mode) {
        if (keyword == null) {
            return;
        }
        switch (mode == null ? TextFilterMode.LIKE : mode) {
            case EXACT -> wrapper.eq(column, keyword);
            case NE -> wrapper.ne(column, keyword);
            case LEFT -> wrapper.likeRight(column, keyword);
            case RIGHT -> wrapper.likeLeft(column, keyword);
            case BATCH -> {
                List<String> values = TextFilterMode.batchValues(keyword);
                if (!values.isEmpty()) {
                    wrapper.in(column, values);
                }
            }
            default -> wrapper.like(column, keyword);
        }
    }

    /**
     * 区划名称筛选：同时匹配名称与全称，让“广东”这类上级关键词能命中其下属区划；
     * 不等于语义需要两个字段同时不命中，否则会放大结果。
     */
    private void applyRegionNameCondition(LambdaQueryWrapper<SysRegion> wrapper,
                                          String keyword, TextFilterMode mode) {
        if (keyword == null) {
            return;
        }
        TextFilterMode effective = mode == null ? TextFilterMode.LIKE : mode;
        if (effective == TextFilterMode.NE) {
            wrapper.ne(SysRegion::getName, keyword).ne(SysRegion::getFullName, keyword);
            return;
        }
        wrapper.and(q -> {
            applyRegionTextCondition(q, SysRegion::getName, keyword, effective);
            q.or();
            applyRegionTextCondition(q, SysRegion::getFullName, keyword, effective);
        });
    }

    private boolean grantFilterMatch(String grantScope, List<SysRouteCompany> rows) {
        if (!StringUtils.hasText(grantScope)) {
            return true;
        }
        boolean all = rows != null && rows.stream()
                .anyMatch(item -> Objects.equals(0L, item.getCompanyDeptId()));
        return "ALL".equalsIgnoreCase(grantScope) ? all : !all;
    }

    /** 授权范围多选：ALL=授权全部，PARTIAL=按公司授权 */
    private boolean grantScopeMatch(List<String> grantScopes, List<SysRouteCompany> rows) {
        if (CollectionUtils.isEmpty(grantScopes)) {
            return true;
        }
        boolean all = rows != null && rows.stream()
                .anyMatch(item -> Objects.equals(0L, item.getCompanyDeptId()));
        return grantScopes.contains(all ? "ALL" : "PARTIAL");
    }

    private int requireScope(Integer routeScope) {
        if (routeScope == null || (routeScope != SCOPE_ROUTE && routeScope != SCOPE_TRANSFER)) {
            throw new BizException("路由域参数不正确");
        }
        return routeScope;
    }

    /** 短驳/干线为网点间物理段，不参与跨公司授权，授权接口只服务外转/直达 */
    private int requireGrantableScope(Integer routeScope) {
        int scope = requireScope(routeScope);
        if (scope == SCOPE_ROUTE) {
            throw new BizException("短驳/干线路由为网点间物理段，不支持授权");
        }
        return scope;
    }

    private List<Long> validateCompanies(List<Long> companyDeptIds) {
        if (companyDeptIds == null || companyDeptIds.isEmpty()) {
            throw new BizException("请选择授权分公司");
        }
        List<Long> companyIds = companyDeptIds.stream().filter(Objects::nonNull).distinct().toList();
        if (companyIds.isEmpty()) {
            throw new BizException("请选择授权分公司");
        }
        List<SysDept> depts = deptMapper.selectBatchIds(companyIds);
        Map<Long, SysDept> map = depts.stream().collect(Collectors.toMap(SysDept::getId, item -> item));
        for (Long companyId : companyIds) {
            SysDept dept = map.get(companyId);
            if (dept == null || !Objects.equals(2, dept.getDeptType())) {
                throw new BizException("授权对象必须为分公司：" + companyId);
            }
            if (!Boolean.TRUE.equals(dept.getStatus())) {
                throw new BizException("分公司已停用，不能授权：" + dept.getDeptName());
            }
        }
        return companyIds;
    }

    private String grantSourceName(Integer source) {
        if (Objects.equals(3, source)) {
            return "总部全部";
        }
        if (Objects.equals(2, source)) {
            return "手动授权";
        }
        return "创建自动";
    }

    /* ==================== 内部：校验与编码 ==================== */

    private SysDept requireRouteDept(Long deptId, String label, Set<Long> scope) {
        SysDept dept = deptId == null ? null : deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(label + "不存在");
        }
        if (!Boolean.TRUE.equals(dept.getStatus())) {
            throw new BizException(label + "已停用");
        }
        if (!Objects.equals(4, dept.getDeptType()) && !Objects.equals(5, dept.getDeptType())) {
            throw new BizException(label + "必须为分拨中心或网点");
        }
        if (!scope.contains(deptId)) {
            throw new BizException(label + "不在当前管理范围内");
        }
        return dept;
    }

    private void validateTrunkFields(int category, RouteSaveRequest request) {
        if (category != CATEGORY_TRUNK) {
            return;
        }
        if (!StringUtils.hasText(request.getWarehouseArea())) {
            throw new BizException("干线必须填写库区");
        }
        if (request.getMileage() == null) {
            throw new BizException("干线必须填写里程");
        }
        if (request.getTransitHours() == null) {
            throw new BizException("干线必须填写时效");
        }
        if (!StringUtils.hasText(request.getContactName())) {
            throw new BizException("干线必须填写线路联系人");
        }
        if (!StringUtils.hasText(request.getContactPhone())) {
            throw new BizException("干线必须填写联系电话");
        }
        if (!StringUtils.hasText(request.getServiceName())) {
            throw new BizException("干线必须填写线路客服");
        }
        if (!StringUtils.hasText(request.getServicePhone())) {
            throw new BizException("干线必须填写客服电话");
        }
    }

    private String resolveRouteName(String input, int category, SysDept origin, SysDept dest) {
        if (StringUtils.hasText(input)) {
            return input.trim();
        }
        // 干线默认“到达网点名+线路”；短驳用始发-到达兜底，保证名称非空
        return category == CATEGORY_TRUNK
                ? dest.getDeptName() + "线路"
                : origin.getDeptName() + "-" + dest.getDeptName();
    }

    private SysRegion requireRegion(String code) {
        SysRegion region = StringUtils.hasText(code) ? regionMapper.selectById(code.trim()) : null;
        if (region == null || !Objects.equals(1, region.getStatus())) {
            throw new BizException("目的地区划不存在或已停用");
        }
        if (region.getLevel() == null || region.getLevel() < 2 || region.getLevel() > 4) {
            throw new BizException("目的地请选择市、区县或乡级区划");
        }
        return region;
    }

    private String regionFullName(SysRegion region) {
        return StringUtils.hasText(region.getFullName()) ? region.getFullName() : region.getName();
    }

    private Long resolveCarrierId(int type, Long carrierId, Set<Long> scope) {
        if (type == TRANSFER_DIRECT) {
            // 直达不允许挂载承运商，切换时自动清空
            return null;
        }
        if (carrierId == null) {
            return null;
        }
        SysCarrier carrier = carrierMapper.selectById(carrierId);
        if (carrier == null || !Boolean.TRUE.equals(carrier.getStatus())) {
            throw new BizException("承运商不存在或已停用");
        }
        if (!scope.contains(carrier.getDeptId())) {
            throw new BizException("承运商不在当前管理范围内");
        }
        return carrierId;
    }

    private String resolveTransferName(String input, SysRegion region) {
        return StringUtils.hasText(input) ? input.trim() : regionFullName(region);
    }

    private String nextRouteCode(int category) {
        String prefix = category == CATEGORY_SHORT ? "DB" : "GX";
        String max = routeMapper.maxCode(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        for (int i = 0; i < 1000; i++) {
            String code = prefix + String.format("%06d", seq + i);
            if (routeMapper.countCodeAll(code) == 0) {
                return code;
            }
        }
        throw new BizException("路由编码生成失败，请重试");
    }

    private String transferCode(String regionCode, int type) {
        // D4=B：编码按「适用网点 + 编码」唯一，不加冲突后缀，同一区划在不同网点下编码相同
        return regionCode + (type == TRANSFER_DIRECT ? "_ZT" : "_WZ");
    }

    private void assertCodeFree(Long applyDeptId, String code) {
        if (transferMapper.countCodeInDept(applyDeptId, code) > 0) {
            throw new BizException("同一适用网点下路由编码已存在：" + code);
        }
    }

    /* ==================== 内部：VO 组装 ==================== */

    private RouteVO toRouteVO(SysRoute item, Map<Long, SysDept> depts, List<SysRouteCompany> grants,
                             Map<Long, String> names, UserOrgService.UserOrg org) {
        RouteVO vo = new RouteVO();
        vo.setId(item.getId());
        vo.setRouteCode(item.getRouteCode());
        vo.setRouteCategory(item.getRouteCategory());
        vo.setOriginDeptId(item.getOriginDeptId());
        vo.setOriginDeptName(deptName(depts, item.getOriginDeptId()));
        vo.setDestDeptId(item.getDestDeptId());
        vo.setDestDeptName(deptName(depts, item.getDestDeptId()));
        vo.setRouteName(item.getRouteName());
        vo.setWarehouseArea(item.getWarehouseArea());
        vo.setMileage(item.getMileage());
        vo.setTransitHours(item.getTransitHours());
        vo.setContactName(item.getContactName());
        vo.setContactPhone(item.getContactPhone());
        vo.setServiceName(item.getServiceName());
        vo.setServicePhone(item.getServicePhone());
        vo.setOwnerCompanyId(item.getOwnerCompanyId());
        vo.setOwnerCompanyName(deptName(depts, item.getOwnerCompanyId()));
        vo.setStatus(item.getStatus());
        vo.setSortOrder(item.getSortOrder());
        vo.setRemark(item.getRemark());
        vo.setCreateByName(names.get(item.getCreateBy()));
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateByName(names.get(item.getUpdateBy()));
        vo.setUpdateTime(item.getUpdateTime());
        boolean hq = org.hq();
        vo.setDeletable(hq || Objects.equals(item.getOwnerCompanyId(), org.companyId()));
        vo.setEditable(hq || Objects.equals(item.getOwnerCompanyId(), org.companyId()));
        return vo;
    }

    private RouteTransferVO toTransferVO(SysRouteTransfer item, Map<Long, SysDept> depts,
                                         Map<Long, SysCarrier> carriers, List<SysRouteCompany> grants,
                                         Map<Long, String> names, UserOrgService.UserOrg org) {
        RouteTransferVO vo = new RouteTransferVO();
        vo.setId(item.getId());
        vo.setRouteCode(item.getRouteCode());
        vo.setRouteName(item.getRouteName());
        vo.setTransferType(item.getTransferType());
        vo.setApplyDeptId(item.getApplyDeptId());
        vo.setApplyDeptName(deptName(depts, item.getApplyDeptId()));
        vo.setRegionCode(item.getRegionCode());
        vo.setRegionLevel(item.getRegionLevel());
        vo.setRegionFullName(item.getRegionFullName());
        vo.setCarrierId(item.getCarrierId());
        SysCarrier carrier = item.getCarrierId() == null ? null : carriers.get(item.getCarrierId());
        vo.setCarrierName(carrier == null ? null : carrier.getCarrierName());
        vo.setOwnerCompanyId(item.getOwnerCompanyId());
        vo.setOwnerCompanyName(deptName(depts, item.getOwnerCompanyId()));
        vo.setStatus(item.getStatus());
        vo.setSortOrder(item.getSortOrder());
        vo.setRemark(item.getRemark());
        vo.setCreateByName(names.get(item.getCreateBy()));
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateByName(names.get(item.getUpdateBy()));
        vo.setUpdateTime(item.getUpdateTime());
        boolean hq = org.hq();
        if (hq) {
            boolean all = hasAllGrant(grants);
            vo.setGrantScope(all ? "ALL" : "COUNT");
            vo.setGrantCount(grantCount(grants));
            // 0 号行为总部自身授权，不算他公司在用
            vo.setDeletable(grants == null || grants.stream()
                    .noneMatch(row -> !Objects.equals(0L, row.getCompanyDeptId())));
        } else {
            vo.setDeletable(Objects.equals(item.getOwnerCompanyId(), org.companyId())
                    && (grants == null || grants.stream()
                    .allMatch(row -> Objects.equals(row.getCompanyDeptId(), org.companyId()))));
        }
        vo.setEditable(hq || Objects.equals(item.getOwnerCompanyId(), org.companyId()));
        return vo;
    }

    private boolean hasAllGrant(List<SysRouteCompany> rows) {
        return rows != null && rows.stream().anyMatch(item -> Objects.equals(0L, item.getCompanyDeptId()));
    }

    private Integer grantCount(List<SysRouteCompany> rows) {
        if (rows == null) {
            return 0;
        }
        return (int) rows.stream().filter(item -> !Objects.equals(0L, item.getCompanyDeptId())).count();
    }

    private String deptName(Map<Long, SysDept> depts, Long deptId) {
        SysDept dept = deptId == null ? null : depts.get(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    private Map<Long, String> resolveUserNames(Set<Long> ids) {
        Set<Long> valid = ids.stream().filter(id -> id != null && id > 0).collect(Collectors.toSet());
        if (valid.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        deptMapper.selectUserNames(valid).forEach(row -> {
            Object id = row.get("id");
            Object realName = row.get("realName");
            if (id != null) {
                names.put(((Number) id).longValue(), realName == null ? null : String.valueOf(realName));
            }
        });
        return names;
    }

    /* ==================== 内部：排序 ==================== */

    private Comparator<SysRoute> buildRouteComparator(RouteQuery query, Map<Long, SysDept> depts,
                                                      Map<Long, List<SysRouteCompany>> grants) {
        Comparator<SysRoute> fallback = Comparator.comparing(SysRoute::getSortOrder)
                .thenComparing(SysRoute::getId);
        if (!StringUtils.hasText(query.getSortBy())) {
            return fallback;
        }
        Comparator<SysRoute> comparator = switch (query.getSortBy()) {
            case "routeCode" -> Comparator.comparing(SysRoute::getRouteCode,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "routeName" -> Comparator.comparing(SysRoute::getRouteName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "routeCategory" -> Comparator.comparing(SysRoute::getRouteCategory,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "originDeptName" -> Comparator.<SysRoute, String>comparing(
                    item -> deptName(depts, item.getOriginDeptId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "destDeptName" -> Comparator.<SysRoute, String>comparing(
                    item -> deptName(depts, item.getDestDeptId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "mileage" -> Comparator.comparing(SysRoute::getMileage,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "transitHours" -> Comparator.comparing(SysRoute::getTransitHours,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "ownerCompanyName" -> Comparator.<SysRoute, String>comparing(
                    item -> deptName(depts, item.getOwnerCompanyId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SysRoute::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "sortOrder" -> Comparator.comparing(SysRoute::getSortOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "createTime" -> Comparator.comparing(SysRoute::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateTime" -> Comparator.comparing(SysRoute::getUpdateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "grantScope" -> Comparator.<SysRoute, Boolean>comparing(
                    item -> hasAllGrant(grants.get(item.getId())), Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator == null) {
            return fallback;
        }
        if ("desc".equalsIgnoreCase(query.getSortOrder())) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SysRoute::getId);
    }

    private Comparator<SysRouteTransfer> buildTransferComparator(TransferQuery query, Map<Long, SysDept> depts,
                                                                 Map<Long, List<SysRouteCompany>> grants,
                                                                 Map<Long, String> names) {
        // 默认排序对齐 luohuo：sort_order asc, id desc
        Comparator<SysRouteTransfer> fallback = Comparator.comparing(SysRouteTransfer::getSortOrder)
                .thenComparing(SysRouteTransfer::getId, Comparator.reverseOrder());
        if (!StringUtils.hasText(query.getSortBy())) {
            return fallback;
        }
        Comparator<SysRouteTransfer> comparator = switch (query.getSortBy()) {
            case "routeCode" -> Comparator.comparing(SysRouteTransfer::getRouteCode,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "routeName" -> Comparator.comparing(SysRouteTransfer::getRouteName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "transferType" -> Comparator.comparing(SysRouteTransfer::getTransferType,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "applyDeptName" -> Comparator.<SysRouteTransfer, String>comparing(
                    item -> deptName(depts, item.getApplyDeptId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "regionFullName" -> Comparator.comparing(SysRouteTransfer::getRegionFullName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "regionLevel" -> Comparator.comparing(SysRouteTransfer::getRegionLevel,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateByName" -> Comparator.<SysRouteTransfer, String>comparing(
                    item -> names.get(item.getUpdateBy()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "remark" -> Comparator.comparing(SysRouteTransfer::getRemark,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "ownerCompanyName" -> Comparator.<SysRouteTransfer, String>comparing(
                    item -> deptName(depts, item.getOwnerCompanyId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SysRouteTransfer::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "sortOrder" -> Comparator.comparing(SysRouteTransfer::getSortOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "createTime" -> Comparator.comparing(SysRouteTransfer::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateTime" -> Comparator.comparing(SysRouteTransfer::getUpdateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "grantScope" -> Comparator.<SysRouteTransfer, Boolean>comparing(
                    item -> hasAllGrant(grants.get(item.getId())), Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator == null) {
            return fallback;
        }
        if ("desc".equalsIgnoreCase(query.getSortOrder())) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SysRouteTransfer::getId);
    }

    /* ==================== 内部：开单匹配 ==================== */

    private record Cand(int rank, BigDecimal transit, BigDecimal mileage, int hops, int sortOrder, Long id,
                        List<RouteMatchVO.RouteMatchNodeVO> nodes) {
    }

    private Map<String, Integer> regionDepths(String destRegionCode, Long destDeptId) {
        Map<String, Integer> depths = new HashMap<>();
        if (StringUtils.hasText(destRegionCode)) {
            depths.put(destRegionCode.trim(), 0);
            SysRegion region = regionMapper.selectById(destRegionCode.trim());
            int depth = 1;
            while (region != null && depth <= 2) {
                if (!StringUtils.hasText(region.getParentCode()) || "0".equals(region.getParentCode())) {
                    break;
                }
                region = regionMapper.selectById(region.getParentCode());
                if (region == null || region.getLevel() == null || region.getLevel() < 2) {
                    break;
                }
                depths.putIfAbsent(region.getCode(), depth);
                depth++;
            }
        } else if (destDeptId != null) {
            SysDept dept = deptMapper.selectById(destDeptId);
            if (dept != null) {
                if (StringUtils.hasText(dept.getDistrictCode())) {
                    depths.put(dept.getDistrictCode(), 0);
                }
                if (StringUtils.hasText(dept.getCityCode())) {
                    depths.putIfAbsent(dept.getCityCode(), 1);
                }
                if (StringUtils.hasText(dept.getProvinceCode())) {
                    depths.putIfAbsent(dept.getProvinceCode(), 2);
                }
            }
        }
        return depths;
    }

    private Set<Long> resolveDestDeptIds(Long destDeptId, Set<String> regionCodes, Map<Long, SysDept> depts) {
        Set<Long> result = new LinkedHashSet<>();
        if (destDeptId != null) {
            result.add(destDeptId);
            return result;
        }
        if (regionCodes.isEmpty()) {
            return result;
        }
        depts.values().forEach(dept -> {
            if (regionCodes.contains(dept.getDistrictCode())
                    || regionCodes.contains(dept.getCityCode())
                    || regionCodes.contains(dept.getProvinceCode())) {
                result.add(dept.getId());
            }
        });
        return result;
    }

    private void walkChains(Long current, Map<Long, List<SysRoute>> adjacency, Set<Long> destDeptIds,
                            List<SysRoute> chain, Set<Long> visited, List<List<SysRoute>> out) {
        if (chain.size() >= 5 || out.size() >= MAX_MATCH_CANDIDATES) {
            return;
        }
        for (SysRoute route : adjacency.getOrDefault(current, List.of())) {
            Long next = route.getDestDeptId();
            if (next == null || !visited.add(next)) {
                continue;
            }
            chain.add(route);
            if (destDeptIds.contains(next)) {
                out.add(new ArrayList<>(chain));
            } else {
                walkChains(next, adjacency, destDeptIds, chain, visited, out);
            }
            chain.remove(chain.size() - 1);
            visited.remove(next);
        }
    }

    private Cand toNetworkCand(List<SysRoute> chain, Map<Long, SysDept> depts) {
        BigDecimal transit = BigDecimal.ZERO;
        BigDecimal mileage = BigDecimal.ZERO;
        boolean transitMissing = false;
        boolean mileageMissing = false;
        List<RouteMatchVO.RouteMatchNodeVO> nodes = new ArrayList<>();
        for (SysRoute item : chain) {
            if (item.getTransitHours() == null) {
                transitMissing = true;
            } else {
                transit = transit.add(item.getTransitHours());
            }
            if (item.getMileage() == null) {
                mileageMissing = true;
            } else {
                mileage = mileage.add(item.getMileage());
            }
            RouteMatchVO.RouteMatchNodeVO node = new RouteMatchVO.RouteMatchNodeVO();
            node.setRouteScope(SCOPE_ROUTE);
            node.setRouteId(item.getId());
            node.setRouteCode(item.getRouteCode());
            node.setCategory(item.getRouteCategory());
            node.setRouteName(item.getRouteName());
            node.setFromDeptId(item.getOriginDeptId());
            node.setFromDeptName(deptName(depts, item.getOriginDeptId()));
            node.setToDeptId(item.getDestDeptId());
            node.setToDeptName(deptName(depts, item.getDestDeptId()));
            node.setContactName(item.getContactName());
            node.setContactPhone(item.getContactPhone());
            node.setTransitHours(item.getTransitHours());
            node.setMileage(item.getMileage());
            node.setWarehouseArea(item.getWarehouseArea());
            node.setServiceName(item.getServiceName());
            node.setServicePhone(item.getServicePhone());
            nodes.add(node);
        }
        SysRoute first = chain.get(0);
        SysRoute last = chain.get(chain.size() - 1);
        return new Cand(1, transitMissing ? null : transit, mileageMissing ? null : mileage, chain.size(),
                first.getSortOrder() == null ? 0 : first.getSortOrder(), last.getId(), nodes);
    }

    /* ==================== 工具 ==================== */

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    /** 逗号分隔 ID：空值返回空集合，非法片段忽略 */
    private Set<Long> parseIds(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            try {
                if (StringUtils.hasText(part)) {
                    result.add(Long.parseLong(part.trim()));
                }
            } catch (NumberFormatException ignored) {
                // 非法 ID 不参与过滤
            }
        }
        return result;
    }

    /** 数值区间；边界为空表示不限，值本身为空时仅在无边界约束下命中 */
    private boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (min == null || value.compareTo(min) >= 0)
                && (max == null || value.compareTo(max) <= 0);
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
