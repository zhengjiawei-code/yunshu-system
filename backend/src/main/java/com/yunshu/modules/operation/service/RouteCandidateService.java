package com.yunshu.modules.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.vo.RouteCandidateVO;
import com.yunshu.modules.system.carrier.entity.SysCarrier;
import com.yunshu.modules.system.carrier.mapper.SysCarrierMapper;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.region.entity.SysRegion;
import com.yunshu.modules.system.region.mapper.SysRegionMapper;
import com.yunshu.modules.system.route.entity.SysRoute;
import com.yunshu.modules.system.route.entity.SysRouteCompany;
import com.yunshu.modules.system.route.entity.SysRouteTransfer;
import com.yunshu.modules.system.route.mapper.SysRouteCompanyMapper;
import com.yunshu.modules.system.route.mapper.SysRouteMapper;
import com.yunshu.modules.system.route.mapper.SysRouteTransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开单路由拼接（方案第 6 章）。
 * 链路只允许两种形态：O --E2--> R，或 O --E1*--> D --E2--> R。
 * 排序：节点数升序 → 末段直达优先 → 区域匹配深度（区县>市>省）→ 总时效 → 总里程 → 末段排序号 → 末段 id。
 * 路由两表豁免数据权限，公司授权在服务层显式过滤。
 */
@Service
@RequiredArgsConstructor
public class RouteCandidateService {

    private static final int MAX_CANDIDATES = 50;
    private static final int MAX_E1_EDGES = 4;
    private static final int MAX_DEST_DISTRICTS = 30;
    private static final int SCOPE_ROUTE = 1;
    private static final int SCOPE_TRANSFER = 2;
    private static final int TYPE_DIRECT = 1;

    private final SysRouteMapper routeMapper;
    private final SysRouteTransferMapper transferMapper;
    private final SysRouteCompanyMapper companyMapper;
    private final SysCarrierMapper carrierMapper;
    private final SysRegionMapper regionMapper;
    private final UserOrgService userOrgService;

    /** 目的地搜索：按区县/市/省名称匹配，展开为「目的地 × 可行路线」 */
    public List<RouteCandidateVO> searchDestinations(String keyword, Long originDeptId) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException("请输入目的地关键字");
        }
        String kw = keyword.trim();
        LinkedHashSet<String> districtCodes = new LinkedHashSet<>();

        List<SysRegion> districts = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getLevel, 3)
                .eq(SysRegion::getStatus, 1)
                .like(SysRegion::getName, kw)
                .orderByAsc(SysRegion::getCode)
                .last("LIMIT " + MAX_DEST_DISTRICTS));
        districts.forEach(item -> districtCodes.add(item.getCode()));

        List<SysRegion> cities = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getLevel, 2)
                .eq(SysRegion::getStatus, 1)
                .like(SysRegion::getName, kw)
                .orderByAsc(SysRegion::getCode)
                .last("LIMIT 10"));
        for (SysRegion city : cities) {
            districtCodes.addAll(childDistricts(city.getCode()));
        }

        List<SysRegion> provinces = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                .eq(SysRegion::getLevel, 1)
                .eq(SysRegion::getStatus, 1)
                .like(SysRegion::getName, kw)
                .orderByAsc(SysRegion::getCode)
                .last("LIMIT 5"));
        for (SysRegion province : provinces) {
            List<SysRegion> provinceCities = regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                    .eq(SysRegion::getLevel, 2)
                    .eq(SysRegion::getParentCode, province.getCode())
                    .eq(SysRegion::getStatus, 1)
                    .orderByAsc(SysRegion::getCode)
                    .last("LIMIT 30"));
            for (SysRegion city : provinceCities) {
                districtCodes.addAll(childDistricts(city.getCode()));
                if (districtCodes.size() >= MAX_DEST_DISTRICTS) {
                    break;
                }
            }
            if (districtCodes.size() >= MAX_DEST_DISTRICTS) {
                break;
            }
        }

        List<String> limited = districtCodes.stream().limit(MAX_DEST_DISTRICTS).toList();
        RouteContext context = loadContext(limited);
        List<RouteCandidateVO> result = new ArrayList<>();
        for (String code : limited) {
            List<RouteCandidateVO> found = candidatesFor(code, originDeptId, context);
            if (found.isEmpty()) {
                result.add(noRouteItem(code, context));
            } else {
                result.addAll(found);
            }
        }
        result.sort(candidateComparator());
        result.stream().limit(MAX_CANDIDATES).forEach(item -> item.setRecommended(false));
        List<RouteCandidateVO> limitedResult = result.stream().limit(MAX_CANDIDATES).toList();
        limitedResult.stream().filter(item -> !Boolean.TRUE.equals(item.getNoRoute())).findFirst()
                .ifPresent(item -> item.setRecommended(true));
        return limitedResult;
    }

    /** 按区县编码返回候选（保存时二次匹配与前端切换共用） */
    public List<RouteCandidateVO> candidates(String districtCode, Long originDeptId) {
        if (!StringUtils.hasText(districtCode)) {
            throw new BizException("请从候选列表中选择唯一目的地");
        }
        RouteContext context = loadContext(List.of(districtCode.trim()));
        List<RouteCandidateVO> result = candidatesFor(districtCode.trim(), originDeptId, context);
        result.sort(candidateComparator());
        List<RouteCandidateVO> limited = result.stream().limit(MAX_CANDIDATES).toList();
        limited.stream().filter(item -> !Boolean.TRUE.equals(item.getNoRoute())).findFirst()
                .ifPresent(item -> item.setRecommended(true));
        return limited;
    }

    private List<String> childDistricts(String cityCode) {
        return regionMapper.selectList(Wrappers.<SysRegion>lambdaQuery()
                        .eq(SysRegion::getLevel, 3)
                        .eq(SysRegion::getParentCode, cityCode)
                        .eq(SysRegion::getStatus, 1)
                        .orderByAsc(SysRegion::getCode)
                        .last("LIMIT 20"))
                .stream().map(SysRegion::getCode).toList();
    }

    /* ==================== 内部：上下文加载 ==================== */

    private RouteContext loadContext(List<String> districtCodes) {
        UserOrgService.UserOrg org = userOrgService.current();
        Long company = org.hq() ? null : org.companyId();
        Map<Long, SysDept> depts = userOrgService.deptMap();
        Long companyId = company;

        List<SysRoute> allRoutes = routeMapper.selectList(Wrappers.<SysRoute>lambdaQuery()
                .eq(SysRoute::getStatus, true));
        if (allRoutes.size() > 5000) {
            throw new BizException("路由数据量超出匹配上限，请联系管理员");
        }
        List<SysRoute> routes = allRoutes.stream()
                .filter(item -> companyId == null || Objects.equals(1L, item.getOwnerCompanyId())
                        || Objects.equals(companyId, item.getOwnerCompanyId()))
                .filter(item -> activeDept(depts, item.getOriginDeptId()) && activeDept(depts, item.getDestDeptId()))
                .toList();

        Set<String> regionCodes = new HashSet<>();
        for (String code : districtCodes) {
            SysRegion district = regionMapper.selectById(code);
            if (district == null) {
                continue;
            }
            regionCodes.add(district.getCode());
            if (StringUtils.hasText(district.getParentCode())) {
                regionCodes.add(district.getParentCode());
                SysRegion city = regionMapper.selectById(district.getParentCode());
                if (city != null && StringUtils.hasText(city.getParentCode())) {
                    regionCodes.add(city.getParentCode());
                }
            }
        }
        if (regionCodes.isEmpty()) {
            return new RouteContext(routes, List.of(), depts, Map.of());
        }

        Set<Long> granted = new HashSet<>();
        if (companyId != null) {
            companyMapper.selectList(Wrappers.<SysRouteCompany>lambdaQuery()
                            .eq(SysRouteCompany::getRouteScope, SCOPE_TRANSFER)
                            .in(SysRouteCompany::getCompanyDeptId, List.of(companyId, 0L)))
                    .forEach(item -> granted.add(item.getRouteId()));
        }
        List<SysRouteTransfer> transfers = transferMapper.selectList(Wrappers.<SysRouteTransfer>lambdaQuery()
                        .in(SysRouteTransfer::getRegionCode, regionCodes)
                        .eq(SysRouteTransfer::getStatus, true))
                .stream()
                .filter(item -> companyId == null
                        || Objects.equals(companyId, item.getOwnerCompanyId())
                        || granted.contains(item.getId()))
                .filter(item -> activeDept(depts, item.getApplyDeptId()))
                .toList();
        return new RouteContext(routes, transfers, depts, loadCarriers(transfers));
    }

    private Map<Long, String> loadCarriers(List<SysRouteTransfer> transfers) {
        Set<Long> ids = transfers.stream().map(SysRouteTransfer::getCarrierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        carrierMapper.selectList(Wrappers.<SysCarrier>lambdaQuery().in(SysCarrier::getId, ids))
                .forEach(item -> names.put(item.getId(), item.getCarrierName()));
        return names;
    }

    private boolean activeDept(Map<Long, SysDept> depts, Long deptId) {
        SysDept dept = deptId == null ? null : depts.get(deptId);
        return dept != null && Boolean.TRUE.equals(dept.getStatus());
    }

    /* ==================== 内部：候选生成 ==================== */

    private List<RouteCandidateVO> candidatesFor(String districtCode, Long originDeptId, RouteContext context) {
        List<RouteCandidateVO> result = new ArrayList<>();
        SysRegion district = regionMapper.selectById(districtCode);
        if (district == null || district.getLevel() == null || district.getLevel() != 3) {
            return result;
        }
        SysRegion city = StringUtils.hasText(district.getParentCode())
                ? regionMapper.selectById(district.getParentCode()) : null;
        SysRegion province = city != null && StringUtils.hasText(city.getParentCode())
                ? regionMapper.selectById(city.getParentCode()) : null;

        Map<String, Integer> levelOf = new LinkedHashMap<>();
        levelOf.put(district.getCode(), 3);
        if (city != null) {
            levelOf.put(city.getCode(), 2);
        }
        if (province != null) {
            levelOf.put(province.getCode(), 1);
        }

        Map<Long, List<SysRouteTransfer>> byDept = new HashMap<>();
        Map<Long, Integer> bestLevel = new HashMap<>();
        for (SysRouteTransfer transfer : context.transfers()) {
            Integer level = levelOf.get(transfer.getRegionCode());
            if (level == null) {
                continue;
            }
            int currentBest = bestLevel.getOrDefault(transfer.getApplyDeptId(), 0);
            if (level > currentBest) {
                bestLevel.put(transfer.getApplyDeptId(), level);
                byDept.put(transfer.getApplyDeptId(), new ArrayList<>(List.of(transfer)));
            } else if (level == currentBest) {
                byDept.get(transfer.getApplyDeptId()).add(transfer);
            }
        }

        Long origin = originDeptId;
        if (origin == null || !context.depts().containsKey(origin)) {
            return result;
        }
        Map<Long, List<SysRoute>> adjacency = new HashMap<>();
        context.routes().forEach(route -> adjacency
                .computeIfAbsent(route.getOriginDeptId(), key -> new ArrayList<>()).add(route));

        Set<String> seen = new HashSet<>();
        walk(origin, adjacency, byDept, new ArrayList<>(), new HashSet<>(Set.of(origin)), context,
                district, city, province, result, seen);

        result.forEach(item -> item.setRecommended(false));
        result.forEach(item -> item.setNoRoute(false));
        return result;
    }

    private void walk(Long current, Map<Long, List<SysRoute>> adjacency,
                      Map<Long, List<SysRouteTransfer>> byDept, List<SysRoute> chain, Set<Long> visited,
                      RouteContext context, SysRegion district, SysRegion city, SysRegion province,
                      List<RouteCandidateVO> out, Set<String> seen) {
        for (SysRouteTransfer transfer : byDept.getOrDefault(current, List.of())) {
            RouteCandidateVO candidate = build(chain, transfer, context, district, city, province);
            String key = String.join(",", candidate.getRouteKeys());
            if (seen.add(key)) {
                out.add(candidate);
            }
        }
        if (chain.size() >= MAX_E1_EDGES || out.size() >= MAX_CANDIDATES) {
            return;
        }
        for (SysRoute route : adjacency.getOrDefault(current, List.of())) {
            Long next = route.getDestDeptId();
            if (next == null || !visited.add(next)) {
                continue;
            }
            chain.add(route);
            walk(next, adjacency, byDept, chain, visited, context, district, city, province, out, seen);
            chain.remove(chain.size() - 1);
            visited.remove(next);
        }
    }

    private RouteCandidateVO build(List<SysRoute> chain, SysRouteTransfer transfer, RouteContext context,
                                   SysRegion district, SysRegion city, SysRegion province) {
        RouteCandidateVO vo = new RouteCandidateVO();
        List<RouteCandidateVO.Node> nodes = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal mileage = BigDecimal.ZERO;
        boolean hoursMissing = false;
        boolean mileageMissing = false;
        int seq = 1;
        List<String> names = new ArrayList<>();

        for (SysRoute route : chain) {
            RouteCandidateVO.Node node = new RouteCandidateVO.Node();
            node.setSeq(seq++);
            node.setRouteScope(SCOPE_ROUTE);
            node.setRouteId(route.getId());
            node.setRouteCode(route.getRouteCode());
            node.setRouteCategory(route.getRouteCategory());
            node.setFromDeptId(route.getOriginDeptId());
            node.setFromDeptName(deptName(context.depts(), route.getOriginDeptId()));
            node.setToDeptId(route.getDestDeptId());
            node.setToDeptName(deptName(context.depts(), route.getDestDeptId()));
            node.setMileage(route.getMileage());
            node.setTransitHours(route.getTransitHours());
            node.setWarehouseArea(route.getWarehouseArea());
            nodes.add(node);
            keys.add(SCOPE_ROUTE + ":" + route.getId());
            if (route.getTransitHours() == null) {
                hoursMissing = true;
            } else {
                hours = hours.add(route.getTransitHours());
            }
            if (route.getMileage() == null) {
                mileageMissing = true;
            } else {
                mileage = mileage.add(route.getMileage());
            }
            if (names.isEmpty()) {
                names.add(deptName(context.depts(), route.getOriginDeptId()));
            }
            names.add(deptName(context.depts(), route.getDestDeptId()));
        }

        RouteCandidateVO.Node last = new RouteCandidateVO.Node();
        last.setSeq(seq);
        last.setRouteScope(SCOPE_TRANSFER);
        last.setRouteId(transfer.getId());
        last.setRouteCode(transfer.getRouteCode());
        last.setTransferType(transfer.getTransferType());
        last.setFromDeptId(transfer.getApplyDeptId());
        last.setFromDeptName(deptName(context.depts(), transfer.getApplyDeptId()));
        last.setDestRegionCode(transfer.getRegionCode());
        last.setDestRegionName(transfer.getRegionFullName());
        last.setCarrierId(transfer.getCarrierId());
        last.setCarrierName(transfer.getCarrierId() == null ? null
                : context.carrierNames().get(transfer.getCarrierId()));
        nodes.add(last);
        keys.add(SCOPE_TRANSFER + ":" + transfer.getId());
        if (names.isEmpty()) {
            names.add(deptName(context.depts(), transfer.getApplyDeptId()));
        }
        names.add(regionShortName(transfer, district, city, province));

        vo.setNodes(nodes);
        vo.setRouteKeys(keys);
        vo.setNodeCount(chain.size() + 2);
        vo.setEndType(transfer.getTransferType());
        vo.setMatchLevel(transfer.getRegionCode().equals(district.getCode()) ? 3
                : city != null && transfer.getRegionCode().equals(city.getCode()) ? 2 : 1);
        vo.setTotalHours(hoursMissing ? null : hours.setScale(2, java.math.RoundingMode.HALF_UP));
        vo.setTotalMileage(mileageMissing ? null : mileage.setScale(2, java.math.RoundingMode.HALF_UP));
        vo.setChainText(String.join(" → ", names));

        vo.setDistrictCode(district.getCode());
        vo.setDistrictName(district.getName());
        vo.setCityCode(city == null ? null : city.getCode());
        vo.setCityName(city == null ? null : city.getName());
        vo.setProvinceCode(province == null ? null : province.getCode());
        vo.setProvinceName(province == null ? null : province.getName());
        vo.setDestName(joinRegionName(province, city, district));
        SysDept hub = resolveDestHub(district.getCode(), context.depts());
        if (hub == null) {
            // 末段区划本身无归属网点时，再取末段起点/适用机构（若其本身是网点，
            // 如"济南分拨中心 → 广州 → 荔湾区"应取广州网点，而不是链路首节点）
            SysDept applyDept = transfer.getApplyDeptId() == null ? null
                    : context.depts().get(transfer.getApplyDeptId());
            if (isHubDept(applyDept)) {
                hub = applyDept;
            }
        }
        if (hub != null) {
            vo.setDestHubId(hub.getId());
            vo.setDestHubName(hub.getDeptName());
        } else {
            // 末段外转/直达只到区划、无归属网点：运达网点回落为始发网点（链路首节点），
            // 不得取路由中间分拨中心或与始发无关的节点
            RouteCandidateVO.Node originNode = nodes.get(0);
            vo.setDestHubId(originNode.getFromDeptId());
            vo.setDestHubName(originNode.getFromDeptName());
        }
        return vo;
    }

    private RouteCandidateVO noRouteItem(String districtCode, RouteContext context) {
        SysRegion district = regionMapper.selectById(districtCode);
        RouteCandidateVO vo = new RouteCandidateVO();
        vo.setNoRoute(true);
        vo.setRecommended(false);
        vo.setRouteKeys(List.of());
        vo.setNodes(List.of());
        vo.setNodeCount(null);
        if (district != null) {
            SysRegion city = StringUtils.hasText(district.getParentCode())
                    ? regionMapper.selectById(district.getParentCode()) : null;
            SysRegion province = city != null && StringUtils.hasText(city.getParentCode())
                    ? regionMapper.selectById(city.getParentCode()) : null;
            vo.setDistrictCode(district.getCode());
            vo.setDistrictName(district.getName());
            vo.setCityCode(city == null ? null : city.getCode());
            vo.setCityName(city == null ? null : city.getName());
            vo.setProvinceCode(province == null ? null : province.getCode());
            vo.setProvinceName(province == null ? null : province.getName());
            vo.setDestName(joinRegionName(province, city, district));
            vo.setChainText(joinRegionName(province, city, district));
        }
        return vo;
    }

    private String regionShortName(SysRouteTransfer transfer, SysRegion district, SysRegion city, SysRegion province) {
        if (district != null && transfer.getRegionCode().equals(district.getCode())) {
            return district.getName();
        }
        if (city != null && transfer.getRegionCode().equals(city.getCode())) {
            return city.getName();
        }
        if (province != null && transfer.getRegionCode().equals(province.getCode())) {
            return province.getName();
        }
        return transfer.getRegionFullName();
    }

    private String joinRegionName(SysRegion province, SysRegion city, SysRegion district) {
        StringBuilder sb = new StringBuilder();
        if (province != null) {
            sb.append(province.getName());
        }
        if (city != null) {
            sb.append(city.getName());
        }
        if (district != null) {
            sb.append(district.getName());
        }
        return sb.toString();
    }

    private SysDept resolveDestHub(String districtCode, Map<Long, SysDept> depts) {
        return depts.values().stream()
                .filter(this::isHubDept)
                .filter(item -> districtCode.equals(item.getDistrictCode()))
                .min(Comparator.comparing(SysDept::getId))
                .orElse(null);
    }

    private boolean isHubDept(SysDept dept) {
        return dept != null
                && Integer.valueOf(5).equals(dept.getDeptType())
                && Boolean.TRUE.equals(dept.getStatus());
    }

    private String deptName(Map<Long, SysDept> depts, Long deptId) {
        SysDept dept = deptId == null ? null : depts.get(deptId);
        return dept == null ? "" : dept.getDeptName();
    }

    private Comparator<RouteCandidateVO> candidateComparator() {
        return Comparator
                .comparing((RouteCandidateVO item) -> Boolean.TRUE.equals(item.getNoRoute()) ? 1 : 0)
                .thenComparing(item -> item.getNodeCount() == null ? Integer.MAX_VALUE : item.getNodeCount())
                .thenComparing(item -> item.getEndType() == null || item.getEndType() != TYPE_DIRECT ? 1 : 0)
                .thenComparing(item -> item.getMatchLevel() == null ? 0 : -item.getMatchLevel())
                .thenComparing(item -> item.getTotalHours() == null
                        ? Double.MAX_VALUE : item.getTotalHours().doubleValue())
                .thenComparing(item -> item.getTotalMileage() == null
                        ? Double.MAX_VALUE : item.getTotalMileage().doubleValue())
                .thenComparing(item -> item.getRouteKeys().isEmpty() ? Long.MAX_VALUE
                        : parseTailId(item.getRouteKeys().get(item.getRouteKeys().size() - 1)))
                .thenComparing(item -> item.getDestName() == null ? "" : item.getDestName());
    }

    private long parseTailId(String key) {
        int idx = key.indexOf(':');
        if (idx < 0 || idx == key.length() - 1) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(key.substring(idx + 1));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private record RouteContext(List<SysRoute> routes, List<SysRouteTransfer> transfers,
                                Map<Long, SysDept> depts, Map<Long, String> carrierNames) {
    }
}
