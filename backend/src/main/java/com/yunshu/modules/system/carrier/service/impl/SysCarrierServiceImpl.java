package com.yunshu.modules.system.carrier.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.common.filter.TextFilterMode;
import com.yunshu.modules.system.carrier.dto.CarrierQuery;
import com.yunshu.modules.system.carrier.dto.CarrierSaveRequest;
import com.yunshu.modules.system.carrier.entity.SysCarrier;
import com.yunshu.modules.system.carrier.mapper.SysCarrierMapper;
import com.yunshu.modules.system.carrier.service.SysCarrierService;
import com.yunshu.modules.system.carrier.vo.CarrierPageVO;
import com.yunshu.modules.system.carrier.vo.CarrierVO;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 承运商：豁免数据权限表，所有查询显式按管理范围过滤，禁止裸 selectById 后直接返回。
 */
@Service
@RequiredArgsConstructor
public class SysCarrierServiceImpl implements SysCarrierService {

    private final SysCarrierMapper carrierMapper;
    private final SysDeptMapper deptMapper;
    private final UserOrgService userOrgService;

    @Override
    @DS("slave")
    public CarrierPageVO page(CarrierQuery query) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        List<SysCarrier> all = carrierMapper.selectList(Wrappers.<SysCarrier>lambdaQuery());
        List<SysCarrier> visible = all.stream().filter(item -> scope.contains(item.getDeptId())).toList();

        Map<Long, SysDept> depts = userOrgService.deptMap();
        Map<Long, String> userNames = resolveUserNames(visible);

        String code = trim(query.getCarrierCode());
        String name = trim(query.getCarrierName());
        String contact = trim(query.getContact());
        String phone = trim(query.getPhone());
        String updateByName = trim(query.getUpdateByName());
        String remark = trim(query.getRemark());
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());

        List<SysCarrier> filtered = visible.stream()
                .filter(item -> TextFilterMode.match(item.getCarrierCode(), code,
                        TextFilterMode.modeOf(modes, "carrierCode")))
                .filter(item -> TextFilterMode.match(item.getCarrierName(), name,
                        TextFilterMode.modeOf(modes, "carrierName")))
                .filter(item -> TextFilterMode.match(item.getContact(), contact,
                        TextFilterMode.modeOf(modes, "contact")))
                .filter(item -> TextFilterMode.match(item.getPhone(), phone,
                        TextFilterMode.modeOf(modes, "phone")))
                .filter(item -> updateByName == null
                        || TextFilterMode.match(nameOf(userNames, item.getUpdateBy()), updateByName,
                        TextFilterMode.modeOf(modes, "updateByName")))
                .filter(item -> TextFilterMode.match(item.getRemark(), remark,
                        TextFilterMode.modeOf(modes, "remark")))
                .filter(item -> CollectionUtils.isEmpty(query.getDeptId())
                        || query.getDeptId().contains(item.getDeptId()))
                .filter(item -> CollectionUtils.isEmpty(query.getStatus())
                        || query.getStatus().contains(item.getStatus()))
                .sorted(buildComparator(query, depts, userNames))
                .toList();

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20 : Math.min(query.getPageSize(), 10000);
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());

        CarrierPageVO result = new CarrierPageVO();
        result.setRecords(filtered.subList(from, to).stream()
                .map(item -> toVO(item, depts, userNames)).toList());
        result.setTotal(filtered.size());
        return result;
    }

    @Override
    @DS("slave")
    public List<CarrierVO> options() {
        Set<Long> scope = userOrgService.manageDeptIds();
        Map<Long, SysDept> depts = userOrgService.deptMap();
        return carrierMapper.selectList(Wrappers.<SysCarrier>lambdaQuery()
                        .eq(SysCarrier::getStatus, true)
                        .orderByAsc(SysCarrier::getSortOrder)
                        .orderByAsc(SysCarrier::getId))
                .stream()
                .filter(item -> scope.contains(item.getDeptId()))
                .map(item -> toVO(item, depts, Map.of()))
                .toList();
    }

    @Override
    @DS("slave")
    public CarrierVO detail(Long id) {
        SysCarrier carrier = requireVisible(id);
        return toVO(carrier, userOrgService.deptMap(), resolveUserNames(List.of(carrier)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long create(CarrierSaveRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        String code = trim(request.getCarrierCode());
        checkCodeUnique(org.deptId(), code, null);

        SysCarrier carrier = new SysCarrier();
        carrier.setCarrierCode(code);
        carrier.setCarrierName(trim(request.getCarrierName()));
        carrier.setContact(trim(request.getContact()));
        carrier.setPhone(trim(request.getPhone()));
        carrier.setStatus(request.getStatus() == null || request.getStatus());
        carrier.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        carrier.setRemark(trim(request.getRemark()));
        // 豁免表不触发自动填充，归属与审计字段由服务层显式写入
        carrier.setDeptId(org.deptId());
        carrier.setCreateBy(org.userId());
        carrier.setUpdateBy(org.userId());
        carrierMapper.insert(carrier);
        return carrier.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, CarrierSaveRequest request) {
        SysCarrier existing = requireVisible(id);
        String code = trim(request.getCarrierCode());
        checkCodeUnique(existing.getDeptId(), code, id);
        DataScopeContext context = DataScopeContext.current();

        carrierMapper.update(null, Wrappers.<SysCarrier>lambdaUpdate()
                .eq(SysCarrier::getId, id)
                .set(SysCarrier::getCarrierCode, code)
                .set(SysCarrier::getCarrierName, trim(request.getCarrierName()))
                .set(SysCarrier::getContact, trim(request.getContact()))
                .set(SysCarrier::getPhone, trim(request.getPhone()))
                .set(SysCarrier::getStatus, request.getStatus() == null ? existing.getStatus() : request.getStatus())
                .set(SysCarrier::getSortOrder, request.getSortOrder() == null ? 0 : request.getSortOrder())
                .set(SysCarrier::getRemark, trim(request.getRemark()))
                .set(SysCarrier::getUpdateBy, context == null ? null : context.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id) {
        requireVisible(id);
        if (carrierMapper.countRouteRef(id) > 0) {
            throw new BizException("该承运商已被路由引用，不能删除");
        }
        carrierMapper.deleteById(id);
    }

    /* ---------------- 内部实现 ---------------- */

    /** 可见性：必须在当前账号管理范围内，失败统一 403 且不区分记录是否存在 */
    private SysCarrier requireVisible(Long id) {
        SysCarrier carrier = id == null ? null : carrierMapper.selectById(id);
        if (carrier == null) {
            throw new BizException(403, "无权访问该承运商");
        }
        if (!userOrgService.manageDeptIds().contains(carrier.getDeptId())) {
            throw new BizException(403, "无权访问该承运商");
        }
        return carrier;
    }

    private void checkCodeUnique(Long deptId, String code, Long excludeId) {
        Long count = carrierMapper.selectCount(Wrappers.<SysCarrier>lambdaQuery()
                .eq(SysCarrier::getDeptId, deptId)
                .eq(SysCarrier::getCarrierCode, code)
                .ne(excludeId != null, SysCarrier::getId, excludeId));
        if (count > 0) {
            throw new BizException("承运商编码已存在：" + code);
        }
    }

    private Map<Long, String> resolveUserNames(List<SysCarrier> carriers) {
        Set<Long> ids = new HashSet<>();
        carriers.forEach(item -> {
            if (item.getCreateBy() != null) {
                ids.add(item.getCreateBy());
            }
            if (item.getUpdateBy() != null) {
                ids.add(item.getUpdateBy());
            }
        });
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        deptMapper.selectUserNames(ids).forEach(row -> {
            Object id = row.get("id");
            Object realName = row.get("realName");
            if (id != null) {
                names.put(((Number) id).longValue(), realName == null ? null : String.valueOf(realName));
            }
        });
        return names;
    }

    private Comparator<SysCarrier> buildComparator(CarrierQuery query, Map<Long, SysDept> depts,
                                                   Map<Long, String> userNames) {
        Comparator<SysCarrier> fallback = Comparator.comparing(SysCarrier::getSortOrder)
                .thenComparing(SysCarrier::getId);
        if (!StringUtils.hasText(query.getSortBy())) {
            return fallback;
        }
        Comparator<SysCarrier> comparator = switch (query.getSortBy()) {
            case "carrierCode" -> Comparator.comparing(SysCarrier::getCarrierCode,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "carrierName" -> Comparator.comparing(SysCarrier::getCarrierName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "contact" -> Comparator.comparing(SysCarrier::getContact,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "phone" -> Comparator.comparing(SysCarrier::getPhone,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "deptName" -> Comparator.<SysCarrier, String>comparing(
                    item -> deptName(depts, item.getDeptId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(SysCarrier::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "sortOrder" -> Comparator.comparing(SysCarrier::getSortOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "createTime" -> Comparator.comparing(SysCarrier::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateTime" -> Comparator.comparing(SysCarrier::getUpdateTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "updateByName" -> Comparator.<SysCarrier, String>comparing(
                    item -> nameOf(userNames, item.getUpdateBy()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "remark" -> Comparator.comparing(SysCarrier::getRemark,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "id" -> Comparator.comparing(SysCarrier::getId);
            default -> null;
        };
        if (comparator == null) {
            return fallback;
        }
        if ("desc".equalsIgnoreCase(query.getSortOrder())) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SysCarrier::getId);
    }

    private CarrierVO toVO(SysCarrier carrier, Map<Long, SysDept> depts, Map<Long, String> names) {
        CarrierVO vo = new CarrierVO();
        vo.setId(carrier.getId());
        vo.setCarrierCode(carrier.getCarrierCode());
        vo.setCarrierName(carrier.getCarrierName());
        vo.setContact(carrier.getContact());
        vo.setPhone(carrier.getPhone());
        vo.setDeptId(carrier.getDeptId());
        vo.setDeptName(deptName(depts, carrier.getDeptId()));
        vo.setStatus(carrier.getStatus());
        vo.setSortOrder(carrier.getSortOrder());
        vo.setRemark(carrier.getRemark());
        vo.setCreateBy(carrier.getCreateBy());
        vo.setCreateByName(nameOf(names, carrier.getCreateBy()));
        vo.setCreateTime(carrier.getCreateTime());
        vo.setUpdateBy(carrier.getUpdateBy());
        vo.setUpdateByName(nameOf(names, carrier.getUpdateBy()));
        vo.setUpdateTime(carrier.getUpdateTime());
        return vo;
    }

    private String deptName(Map<Long, SysDept> depts, Long deptId) {
        SysDept dept = deptId == null ? null : depts.get(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    private String nameOf(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
