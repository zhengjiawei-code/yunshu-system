package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.common.filter.TextFilterMode;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.QuickDriverRequest;
import com.yunshu.modules.transport.dto.QuickVehicleRequest;
import com.yunshu.modules.transport.dto.TripDestSaveRequest;
import com.yunshu.modules.transport.dto.TripListQuery;
import com.yunshu.modules.transport.dto.TripSaveRequest;
import com.yunshu.modules.transport.dto.TripUpdateRequest;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.entity.SysDriver;
import com.yunshu.modules.transport.entity.SysVehicle;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import com.yunshu.modules.transport.mapper.SysDriverMapper;
import com.yunshu.modules.transport.mapper.SysVehicleMapper;
import com.yunshu.modules.transport.vo.TripDestVO;
import com.yunshu.modules.transport.vo.TripDetailVO;
import com.yunshu.modules.transport.vo.TripOptionVO;
import com.yunshu.modules.transport.vo.TripPageVO;
import com.yunshu.modules.transport.vo.TripSaveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 车次基础能力（B1）：分页、详情、创建/修改/删除、目的网点维护、下拉与车辆司机快速新增。
 * 状态规则：修改仅 10/20；删除仅 10 且无配载；目的网点维护仅非关闭状态，且有配载的网点不可改/删。
 */
@Service
@RequiredArgsConstructor
public class TripService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int TYPE_DISTRIBUTION = 4;
    private static final int TYPE_LEAF = 5;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BizTripMapper tripMapper;
    private final BizTripDestMapper destMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final SysVehicleMapper vehicleMapper;
    private final SysDriverMapper driverMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final UserOrgService userOrgService;
    private final TripNoGenerator tripNoGenerator;
    private final TripLogService tripLogService;

    /* ---------------- 查询 ---------------- */

    @DS("slave")
    public TripPageVO page(TripListQuery query) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        int pageNum = query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);

        QueryWrapper<BizTrip> wrapper = new QueryWrapper<>();
        applyScope(wrapper, org, scope);
        List<Long> destTripIds = resolveDestTripIds(query.getDestDeptId());
        applyFilters(wrapper, query, destTripIds);
        applySort(wrapper, query.getSortBy(), query.getSortOrder());

        Page<BizTrip> page = tripMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        TripPageVO vo = new TripPageVO();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setPage(pageNum);
        vo.setPageSize(pageSize);
        vo.setSummary(summary(query, org, scope));
        return vo;
    }

    @DS("slave")
    public TripDetailVO detail(Long id) {
        BizTrip trip = requireVisible(id);
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, id)
                .orderByAsc(BizTripDest::getSeq)
                .orderByAsc(BizTripDest::getId));
        TripDetailVO vo = new TripDetailVO();
        vo.setTrip(trip);
        vo.setDests(dests.stream().map(this::toDestVO).toList());
        vo.setSummary(toSummary(trip));
        return vo;
    }

    /* ---------------- 车次 CRUD ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TripSaveVO create(TripSaveRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        Long originDeptId = request.getOriginDeptId() == null ? org.deptId() : request.getOriginDeptId();
        SysDept origin = requireDept(originDeptId, scope);
        List<SysDept> destDepts = normalizeDests(request.getDestDeptIds(), scope);

        String operatorName = operatorName(org.userId());
        String tripNo = tripNoGenerator.nextTripNo();

        BizTrip trip = new BizTrip();
        trip.setTripNo(tripNo);
        trip.setTripName(request.getTripName().trim());
        trip.setTripCategory(1);
        trip.setTripStatus(TripEnums.TripStatus.PLAN);
        trip.setOriginDeptId(origin.getId());
        trip.setOriginDeptName(origin.getDeptName());
        trip.setPlanDepartTime(request.getPlanDepartTime());
        trip.setAllocMethod(TripEnums.AllocMethod.CHARGE_WEIGHT);
        trip.setRemark(trim(request.getRemark()));
        applyVehicleSnapshot(trip, request, scope);
        applyDriverSnapshot(trip, request, scope);
        trip.setDeptId(org.deptId());
        trip.setCreateBy(org.userId());
        trip.setCreateByName(operatorName);
        trip.setUpdateBy(org.userId());
        trip.setUpdateByName(operatorName);
        tripMapper.insert(trip);

        List<BizTripDest> dests = insertDests(trip, destDepts, org);
        tripLogService.record(trip.getId(), trip.getTripNo(), TripEnums.LogAction.CREATE, null,
                org.deptId(), org.userId(), operatorName, null, tripSnapshot(trip), null);

        TripSaveVO vo = new TripSaveVO();
        vo.setId(trip.getId());
        vo.setTripNo(trip.getTripNo());
        vo.setSubTrips(dests.stream().map(this::toDestVO).toList());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void update(Long id, TripUpdateRequest request) {
        BizTrip existing = requireVisible(id);
        if (!Objects.equals(TripEnums.TripStatus.PLAN, existing.getTripStatus())
                && !Objects.equals(TripEnums.TripStatus.LOADING, existing.getTripStatus())) {
            throw new BizException("当前车次状态不可修改");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        boolean hasLoads = waybillCount(id) > 0;

        if (request.getDestDeptIds() != null || request.getOriginDeptId() != null) {
            if (!Objects.equals(TripEnums.TripStatus.PLAN, existing.getTripStatus()) || hasLoads) {
                throw new BizException("已配载的车次不允许修改始发网点或送达网点");
            }
        }

        Map<String, Object> before = tripSnapshot(existing);
        BizTrip update = new BizTrip();
        update.setId(id);
        if (request.getTripName() != null) {
            if (!StringUtils.hasText(request.getTripName())) {
                throw new BizException("车次名称不能为空");
            }
            update.setTripName(request.getTripName().trim());
        }
        if (request.getPlanDepartTime() != null) {
            update.setPlanDepartTime(request.getPlanDepartTime());
        }
        if (request.getRemark() != null) {
            update.setRemark(trim(request.getRemark()));
        }
        if (request.getOriginDeptId() != null) {
            SysDept origin = requireDept(request.getOriginDeptId(), scope);
            update.setOriginDeptId(origin.getId());
            update.setOriginDeptName(origin.getDeptName());
        }
        update.setUpdateBy(org.userId());
        update.setUpdateByName(operatorName(org.userId()));
        tripMapper.updateById(update);

        if (request.getDestDeptIds() != null) {
            List<SysDept> destDepts = normalizeDests(request.getDestDeptIds(), scope);
            destMapper.hardDeleteByTripId(id);
            insertDests(tripMapper.selectById(id), destDepts, org);
        }

        BizTrip after = tripMapper.selectById(id);
        tripLogService.record(id, existing.getTripNo(), TripEnums.LogAction.UPDATE, null,
                org.deptId(), org.userId(), operatorName(org.userId()), before, tripSnapshot(after), null);
    }

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void delete(Long id) {
        BizTrip existing = requireVisible(id);
        if (!Objects.equals(TripEnums.TripStatus.PLAN, existing.getTripStatus())) {
            throw new BizException("仅车次计划状态可删除");
        }
        if (waybillCount(id) > 0) {
            throw new BizException("该车次已有配载运单，不能删除");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        tripLogService.record(id, existing.getTripNo(), TripEnums.LogAction.DELETE, null,
                org.deptId(), org.userId(), operatorName(org.userId()), tripSnapshot(existing), null, "删除车次");
        destMapper.delete(Wrappers.<BizTripDest>lambdaQuery().eq(BizTripDest::getTripId, id));
        tripMapper.deleteById(id);
    }

    /* ---------------- 目的网点 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TripDestVO addDest(Long tripId, TripDestSaveRequest request) {
        BizTrip trip = requireVisible(tripId);
        if (Objects.equals(TripEnums.TripStatus.CLOSED, trip.getTripStatus())) {
            throw new BizException("已关闭车次不能新增目的网点");
        }
        if (request.getDestDeptId() == null) {
            throw new BizException("目的网点不能为空");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        SysDept dept = requireDept(request.getDestDeptId(), scope);
        long duplicate = destMapper.selectCount(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId)
                .eq(BizTripDest::getDestDeptId, dept.getId()));
        if (duplicate > 0) {
            throw new BizException("该目的网点已存在");
        }
        int seq = nextSeq(tripId);
        BizTripDest dest = buildDest(trip, dept, seq, request.getPlanArriveTime(), org);
        destMapper.insert(dest);
        tripLogService.record(tripId, trip.getTripNo(), 15, dest.getId(),
                org.deptId(), org.userId(), operatorName(org.userId()), null,
                Map.of("destDeptId", dept.getId(), "seq", seq, "subTripNo", dest.getSubTripNo()), "新增目的网点");
        return toDestVO(dest);
    }

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void updateDest(Long tripId, Long destId, TripDestSaveRequest request) {
        BizTrip trip = requireVisible(tripId);
        if (Objects.equals(TripEnums.TripStatus.CLOSED, trip.getTripStatus())) {
            throw new BizException("已关闭车次不能修改目的网点");
        }
        BizTripDest dest = requireDest(tripId, destId);
        if (destWaybillCount(destId) > 0) {
            throw new BizException("该目的网点已有配载运单，不能修改");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);

        BizTripDest update = new BizTripDest();
        update.setId(destId);
        if (request.getDestDeptId() != null && !Objects.equals(request.getDestDeptId(), dest.getDestDeptId())) {
            SysDept dept = requireDept(request.getDestDeptId(), scope);
            long duplicate = destMapper.selectCount(Wrappers.<BizTripDest>lambdaQuery()
                    .eq(BizTripDest::getTripId, tripId)
                    .eq(BizTripDest::getDestDeptId, dept.getId())
                    .ne(BizTripDest::getId, destId));
            if (duplicate > 0) {
                throw new BizException("该目的网点已存在");
            }
            update.setDestDeptId(dept.getId());
            update.setDestDeptName(dept.getDeptName());
        }
        if (request.getSeq() != null && !Objects.equals(request.getSeq(), dest.getSeq())) {
            int seq = request.getSeq();
            if (seq < 1 || seq > 99) {
                throw new BizException("目的网点顺序号必须在 1-99 之间");
            }
            long occupied = destMapper.selectCount(Wrappers.<BizTripDest>lambdaQuery()
                    .eq(BizTripDest::getTripId, tripId)
                    .eq(BizTripDest::getSeq, seq)
                    .ne(BizTripDest::getId, destId));
            if (occupied > 0) {
                throw new BizException("目的网点顺序号已被占用");
            }
            update.setSeq(seq);
            update.setSubTripNo(trip.getTripNo() + "-" + String.format("%02d", seq));
        }
        if (request.getPlanArriveTime() != null) {
            update.setPlanArriveTime(request.getPlanArriveTime());
        }
        update.setUpdateBy(org.userId());
        destMapper.updateById(update);
        tripLogService.record(tripId, trip.getTripNo(), 15, destId,
                org.deptId(), org.userId(), operatorName(org.userId()), null,
                Map.of("destDeptId", update.getDestDeptId() == null ? dest.getDestDeptId() : update.getDestDeptId(),
                        "seq", update.getSeq() == null ? dest.getSeq() : update.getSeq()), "修改目的网点");
    }

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void deleteDest(Long tripId, Long destId) {
        BizTrip trip = requireVisible(tripId);
        if (Objects.equals(TripEnums.TripStatus.CLOSED, trip.getTripStatus())) {
            throw new BizException("已关闭车次不能删除目的网点");
        }
        BizTripDest dest = requireDest(tripId, destId);
        if (destWaybillCount(destId) > 0) {
            throw new BizException("该目的网点已有配载运单，不能删除");
        }
        long total = destMapper.selectCount(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId));
        if (total <= 1) {
            throw new BizException("车次至少保留一个目的网点");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        destMapper.hardDeleteById(destId);
        tripLogService.record(tripId, trip.getTripNo(), 15, destId,
                org.deptId(), org.userId(), operatorName(org.userId()),
                Map.of("destDeptId", dest.getDestDeptId(), "seq", dest.getSeq()), null, "删除目的网点");
    }

    /* ---------------- 下拉与快速新增 ---------------- */

    @DS("slave")
    public List<TripOptionVO> options(String type, String keyword) {
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        String text = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        String kind = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "dept";

        if ("vehicle".equals(kind)) {
            return vehicleMapper.selectList(Wrappers.<SysVehicle>lambdaQuery()
                            .eq(SysVehicle::getStatus, true)
                            .orderByAsc(SysVehicle::getId))
                    .stream()
                    .filter(item -> scope.contains(item.getDeptId()))
                    .filter(item -> text == null || contains(item.getPlateNo(), text))
                    .map(this::toVehicleOption)
                    .toList();
        }
        if ("driver".equals(kind)) {
            return driverMapper.selectList(Wrappers.<SysDriver>lambdaQuery()
                            .eq(SysDriver::getStatus, true)
                            .orderByAsc(SysDriver::getId))
                    .stream()
                    .filter(item -> scope.contains(item.getDeptId()))
                    .filter(item -> text == null || contains(item.getDriverName(), text)
                            || contains(item.getDriverPhone(), text))
                    .map(this::toDriverOption)
                    .toList();
        }
        if ("vehicleType".equals(kind)) {
            return vehicleMapper.selectList(Wrappers.<SysVehicle>lambdaQuery()
                            .eq(SysVehicle::getStatus, true))
                    .stream()
                    .filter(item -> scope.contains(item.getDeptId()))
                    .filter(item -> StringUtils.hasText(item.getVehicleType()))
                    .map(SysVehicle::getVehicleType)
                    .distinct()
                    .filter(name -> text == null || name.toLowerCase(Locale.ROOT).contains(text))
                    .map(name -> {
                        TripOptionVO vo = new TripOptionVO();
                        vo.setName(name);
                        vo.setType("vehicleType");
                        return vo;
                    })
                    .toList();
        }

        return deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                        .eq(SysDept::getStatus, true)
                        .in(SysDept::getDeptType, TYPE_DISTRIBUTION, TYPE_LEAF)
                        .orderByAsc(SysDept::getSortOrder)
                        .orderByAsc(SysDept::getId))
                .stream()
                .filter(item -> scope.contains(item.getId()))
                .filter(item -> text == null || contains(item.getDeptName(), text))
                .map(this::toDeptOption)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long quickVehicle(QuickVehicleRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        String plateNo = request.getPlateNo().trim().toUpperCase(Locale.ROOT);
        SysVehicle existing = vehicleMapper.selectOne(Wrappers.<SysVehicle>lambdaQuery()
                .eq(SysVehicle::getPlateNo, plateNo)
                .last("LIMIT 1"));
        if (existing != null) {
            if (userOrgService.manageDeptIds(org).contains(existing.getDeptId())) {
                return existing.getId();
            }
            throw new BizException("该车牌号已被其他机构使用");
        }
        SysVehicle vehicle = new SysVehicle();
        vehicle.setPlateNo(plateNo);
        vehicle.setVehicleType(trim(request.getVehicleType()));
        vehicle.setVehicleLoad(request.getVehicleLoad());
        vehicle.setVehicleVolume(request.getVehicleVolume());
        vehicle.setStatus(true);
        vehicle.setDeptId(org.deptId());
        vehicle.setCreateBy(org.userId());
        vehicle.setUpdateBy(org.userId());
        vehicleMapper.insert(vehicle);
        return vehicle.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public Long quickDriver(QuickDriverRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        String name = request.getDriverName().trim();
        String phone = request.getDriverPhone().trim();
        SysDriver existing = driverMapper.selectOne(Wrappers.<SysDriver>lambdaQuery()
                .eq(SysDriver::getDriverName, name)
                .eq(SysDriver::getDriverPhone, phone)
                .eq(SysDriver::getStatus, true)
                .last("LIMIT 1"));
        if (existing != null && userOrgService.manageDeptIds(org).contains(existing.getDeptId())) {
            return existing.getId();
        }
        SysDriver driver = new SysDriver();
        driver.setDriverName(name);
        driver.setDriverPhone(phone);
        driver.setLicenseNo(trim(request.getLicenseNo()));
        driver.setStatus(true);
        driver.setDeptId(org.deptId());
        driver.setCreateBy(org.userId());
        driver.setUpdateBy(org.userId());
        driverMapper.insert(driver);
        return driver.getId();
    }

    /* ---------------- 内部实现 ---------------- */

    BizTrip requireVisible(Long id) {
        BizTrip trip = id == null ? null : tripMapper.selectById(id);
        if (trip == null || !userOrgService.manageDeptIds().contains(trip.getDeptId())) {
            throw new BizException(403, "无权访问该车次");
        }
        return trip;
    }

    private BizTripDest requireDest(Long tripId, Long destId) {
        BizTripDest dest = destId == null ? null : destMapper.selectById(destId);
        if (dest == null || !Objects.equals(tripId, dest.getTripId())) {
            throw new BizException(403, "无权访问该目的网点");
        }
        return dest;
    }

    private SysDept requireDept(Long deptId, Set<Long> scope) {
        if (deptId == null) {
            throw new BizException("网点不能为空");
        }
        if (!scope.contains(deptId)) {
            throw new BizException(403, "无权访问该网点");
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null || !Boolean.TRUE.equals(dept.getStatus())) {
            throw new BizException("网点不存在或已停用");
        }
        if (!Objects.equals(TYPE_DISTRIBUTION, dept.getDeptType())
                && !Objects.equals(TYPE_LEAF, dept.getDeptType())) {
            throw new BizException("只能选择分拨中心或网点");
        }
        return dept;
    }

    private List<SysDept> normalizeDests(List<Long> ids, Set<Long> scope) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("目的网点至少选择1个");
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<SysDept> result = new ArrayList<>();
        for (Long id : unique) {
            result.add(requireDept(id, scope));
        }
        return result;
    }

    private void applyVehicleSnapshot(BizTrip trip, TripSaveRequest request, Set<Long> scope) {
        if (request.getVehicleId() == null) {
            trip.setVehicleId(null);
            trip.setPlateNo(trim(request.getPlateNo()));
            trip.setVehicleType(trim(request.getVehicleType()));
            trip.setVehicleLoad(request.getVehicleLoad());
            trip.setVehicleVolume(request.getVehicleVolume());
            return;
        }
        SysVehicle vehicle = vehicleMapper.selectById(request.getVehicleId());
        if (vehicle == null || !scope.contains(vehicle.getDeptId()) || !Boolean.TRUE.equals(vehicle.getStatus())) {
            throw new BizException(403, "无权使用该车辆");
        }
        trip.setVehicleId(vehicle.getId());
        trip.setPlateNo(vehicle.getPlateNo());
        trip.setVehicleType(vehicle.getVehicleType());
        trip.setVehicleLoad(vehicle.getVehicleLoad());
        trip.setVehicleVolume(vehicle.getVehicleVolume());
    }

    private void applyDriverSnapshot(BizTrip trip, TripSaveRequest request, Set<Long> scope) {
        if (request.getDriverId() == null) {
            trip.setDriverId(null);
            trip.setDriverName(trim(request.getDriverName()));
            trip.setDriverPhone(trim(request.getDriverPhone()));
            return;
        }
        SysDriver driver = driverMapper.selectById(request.getDriverId());
        if (driver == null || !scope.contains(driver.getDeptId()) || !Boolean.TRUE.equals(driver.getStatus())) {
            throw new BizException(403, "无权使用该司机");
        }
        trip.setDriverId(driver.getId());
        trip.setDriverName(driver.getDriverName());
        trip.setDriverPhone(driver.getDriverPhone());
    }

    private List<BizTripDest> insertDests(BizTrip trip, List<SysDept> depts, UserOrgService.UserOrg org) {
        List<BizTripDest> result = new ArrayList<>();
        int seq = 1;
        for (SysDept dept : depts) {
            BizTripDest dest = buildDest(trip, dept, seq++, null, org);
            destMapper.insert(dest);
            result.add(dest);
        }
        return result;
    }

    private BizTripDest buildDest(BizTrip trip, SysDept dept, int seq, LocalDateTime planArriveTime,
                                  UserOrgService.UserOrg org) {
        BizTripDest dest = new BizTripDest();
        dest.setTripId(trip.getId());
        dest.setSubTripNo(trip.getTripNo() + "-" + String.format("%02d", seq));
        dest.setSeq(seq);
        dest.setDestDeptId(dept.getId());
        dest.setDestDeptName(dept.getDeptName());
        dest.setSubStatus(trip.getTripStatus() != null && trip.getTripStatus() >= TripEnums.TripStatus.IN_TRANSIT
                ? TripEnums.SubStatus.IN_TRANSIT : TripEnums.SubStatus.PENDING);
        dest.setPlanArriveTime(planArriveTime);
        dest.setTotalWaybillCount(0);
        dest.setTotalQuantity(0);
        dest.setTotalWeight(BigDecimal.ZERO);
        dest.setTotalVolume(BigDecimal.ZERO);
        dest.setArrivalUnloadFee(BigDecimal.ZERO);
        dest.setArrivalOtherFee(BigDecimal.ZERO);
        dest.setArrivalUnloadFeeValid(true);
        dest.setArrivalOtherFeeValid(true);
        dest.setDeptId(org.deptId());
        dest.setCreateBy(org.userId());
        dest.setUpdateBy(org.userId());
        return dest;
    }

    private int nextSeq(Long tripId) {
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId)
                .orderByDesc(BizTripDest::getSeq)
                .last("LIMIT 1"));
        return dests.isEmpty() || dests.get(0).getSeq() == null ? 1 : dests.get(0).getSeq() + 1;
    }

    private long waybillCount(Long tripId) {
        return tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripId, tripId));
    }

    private long destWaybillCount(Long destId) {
        return tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripDestId, destId));
    }

    private void applyScope(QueryWrapper<BizTrip> wrapper, UserOrgService.UserOrg org, Set<Long> scope) {
        wrapper.eq("deleted", 0);
        if (!org.hq()) {
            wrapper.in("dept_id", scope);
        }
    }

    private void applyFilters(QueryWrapper<BizTrip> wrapper, TripListQuery query, List<Long> destTripIds) {
        Map<String, TextFilterMode> modes = TextFilterMode.parse(query.getFilterModes());
        applyText(wrapper, "trip_no", query.getTripNo(), TextFilterMode.modeOf(modes, "tripNo"));
        applyText(wrapper, "trip_name", query.getTripName(), TextFilterMode.modeOf(modes, "tripName"));
        applyText(wrapper, "plate_no", query.getPlateNo(), TextFilterMode.modeOf(modes, "plateNo"));
        applyText(wrapper, "driver_name", query.getDriverName(), TextFilterMode.modeOf(modes, "driverName"));
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.in("trip_status", query.getStatus());
        }
        if (query.getOriginDeptId() != null) {
            wrapper.eq("origin_dept_id", query.getOriginDeptId());
        }
        if (query.getDestDeptId() != null) {
            // 数据权限拦截器拒绝 WHERE 子查询，改为先查目的网点再按车次 ID 过滤
            if (destTripIds == null || destTripIds.isEmpty()) {
                wrapper.eq("id", -1L);
            } else {
                wrapper.in("id", destTripIds);
            }
        }
        applyRange(wrapper, "plan_depart_time", query.getPlanDepartStart(), query.getPlanDepartEnd());
        applyRange(wrapper, "depart_time", query.getDepartStart(), query.getDepartEnd());
        applyRange(wrapper, "plan_arrive_time", query.getPlanArriveStart(), query.getPlanArriveEnd());
        applyRange(wrapper, "actual_arrive_time", query.getActualArriveStart(), query.getActualArriveEnd());
        applyRange(wrapper, "create_time", query.getCreateStart(), query.getCreateEnd());
    }

    private List<Long> resolveDestTripIds(Long destDeptId) {
        if (destDeptId == null) {
            return null;
        }
        return destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                        .select(BizTripDest::getTripId)
                        .eq(BizTripDest::getDestDeptId, destDeptId)
                        .groupBy(BizTripDest::getTripId))
                .stream()
                .map(BizTripDest::getTripId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void applyText(QueryWrapper<BizTrip> wrapper, String column, String raw, TextFilterMode mode) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        String value = raw.trim();
        switch (mode == null ? TextFilterMode.LIKE : mode) {
            case EXACT -> wrapper.eq(column, value);
            case NE -> wrapper.ne(column, value);
            case LEFT -> wrapper.likeRight(column, value);
            case RIGHT -> wrapper.likeLeft(column, value);
            case BATCH -> {
                List<String> values = TextFilterMode.batchValues(value);
                if (!values.isEmpty()) {
                    wrapper.in(column, values);
                }
            }
            default -> wrapper.like(column, value);
        }
    }

    private void applyRange(QueryWrapper<BizTrip> wrapper, String column, String start, String end) {
        LocalDateTime from = parseStart(start);
        LocalDateTime to = parseEnd(end);
        if (from != null) {
            wrapper.ge(column, from);
        }
        if (to != null) {
            wrapper.le(column, to);
        }
    }

    private void applySort(QueryWrapper<BizTrip> wrapper, String sortBy, String sortOrder) {
        String column = switch (sortBy == null ? "" : sortBy.trim()) {
            case "tripNo" -> "trip_no";
            case "tripName" -> "trip_name";
            case "tripStatus" -> "trip_status";
            case "planDepartTime" -> "plan_depart_time";
            case "departTime" -> "depart_time";
            case "planArriveTime" -> "plan_arrive_time";
            case "actualArriveTime" -> "actual_arrive_time";
            case "plateNo" -> "plate_no";
            case "driverName" -> "driver_name";
            case "totalFreight" -> "total_freight";
            case "grossProfit" -> "gross_profit";
            case "createTime" -> "create_time";
            case "id" -> "id";
            default -> null;
        };
        boolean asc = "asc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
        if (column == null) {
            wrapper.orderByDesc("create_time");
        } else {
            wrapper.orderBy(true, asc, column);
        }
        wrapper.orderByDesc("id");
    }

    private TripPageVO.Summary summary(TripListQuery query, UserOrgService.UserOrg org, Set<Long> scope) {
        QueryWrapper<BizTrip> wrapper = new QueryWrapper<>();
        wrapper.select("COUNT(*) AS tripCount",
                "COALESCE(SUM(total_waybill_count),0) AS waybillCount",
                "COALESCE(SUM(total_quantity),0) AS quantity",
                "COALESCE(SUM(total_weight),0) AS weight",
                "COALESCE(SUM(total_volume),0) AS volume",
                "COALESCE(SUM(total_freight),0) AS totalFreight",
                "COALESCE(SUM(gross_profit),0) AS grossProfit");
        applyScope(wrapper, org, scope);
        applyFilters(wrapper, query, resolveDestTripIds(query.getDestDeptId()));
        List<Map<String, Object>> rows = tripMapper.selectMaps(wrapper);
        TripPageVO.Summary summary = new TripPageVO.Summary();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            summary.setTripCount(toLong(row.get("tripCount")));
            summary.setWaybillCount(toLong(row.get("waybillCount")));
            summary.setQuantity(toLong(row.get("quantity")));
            summary.setWeight(toDecimal(row.get("weight")));
            summary.setVolume(toDecimal(row.get("volume")));
            summary.setTotalFreight(toDecimal(row.get("totalFreight")));
            summary.setGrossProfit(toDecimal(row.get("grossProfit")));
        }
        return summary;
    }

    private TripPageVO.Summary toSummary(BizTrip trip) {
        TripPageVO.Summary summary = new TripPageVO.Summary();
        summary.setTripCount(1);
        summary.setWaybillCount(trip.getTotalWaybillCount() == null ? 0 : trip.getTotalWaybillCount());
        summary.setQuantity(trip.getTotalQuantity() == null ? 0 : trip.getTotalQuantity());
        summary.setWeight(nvl(trip.getTotalWeight()));
        summary.setVolume(nvl(trip.getTotalVolume()));
        summary.setTotalFreight(nvl(trip.getTotalFreight()));
        summary.setGrossProfit(nvl(trip.getGrossProfit()));
        return summary;
    }

    private Map<String, Object> tripSnapshot(BizTrip trip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tripNo", trip.getTripNo());
        map.put("tripName", trip.getTripName());
        map.put("tripStatus", trip.getTripStatus());
        map.put("originDeptId", trip.getOriginDeptId());
        map.put("planDepartTime", trip.getPlanDepartTime() == null ? null : trip.getPlanDepartTime().format(DATE_TIME));
        map.put("plateNo", trip.getPlateNo());
        map.put("driverName", trip.getDriverName());
        map.put("remark", trip.getRemark());
        return map;
    }

    private TripDestVO toDestVO(BizTripDest dest) {
        TripDestVO vo = new TripDestVO();
        vo.setId(dest.getId());
        vo.setTripId(dest.getTripId());
        vo.setSubTripNo(dest.getSubTripNo());
        vo.setSeq(dest.getSeq());
        vo.setDestDeptId(dest.getDestDeptId());
        vo.setDestDeptName(dest.getDestDeptName());
        vo.setSubStatus(dest.getSubStatus());
        vo.setPlanArriveTime(dest.getPlanArriveTime());
        vo.setArriveTime(dest.getArriveTime());
        vo.setUnloadTime(dest.getUnloadTime());
        vo.setTotalWaybillCount(dest.getTotalWaybillCount());
        vo.setTotalQuantity(dest.getTotalQuantity());
        vo.setTotalWeight(dest.getTotalWeight());
        vo.setTotalVolume(dest.getTotalVolume());
        vo.setArrivalUnloadFee(dest.getArrivalUnloadFee());
        vo.setArrivalOtherFee(dest.getArrivalOtherFee());
        vo.setArrivalUnloadFeeValid(dest.getArrivalUnloadFeeValid());
        vo.setArrivalOtherFeeValid(dest.getArrivalOtherFeeValid());
        return vo;
    }

    private TripOptionVO toDeptOption(SysDept dept) {
        TripOptionVO vo = new TripOptionVO();
        vo.setId(dept.getId());
        vo.setName(dept.getDeptName());
        vo.setType("dept");
        vo.setLeaf(dept.getIsLeaf());
        return vo;
    }

    private TripOptionVO toVehicleOption(SysVehicle vehicle) {
        TripOptionVO vo = new TripOptionVO();
        vo.setId(vehicle.getId());
        vo.setName(vehicle.getPlateNo());
        vo.setType("vehicle");
        vo.setLoad(vehicle.getVehicleLoad());
        vo.setVolume(vehicle.getVehicleVolume());
        return vo;
    }

    private TripOptionVO toDriverOption(SysDriver driver) {
        TripOptionVO vo = new TripOptionVO();
        vo.setId(driver.getId());
        vo.setName(driver.getDriverName());
        vo.setPhone(driver.getDriverPhone());
        vo.setType("driver");
        return vo;
    }

    private String operatorName(Long userId) {
        if (userId == null) {
            return "system";
        }
        SysUser user = userMapper.selectById(userId);
        String name = user == null ? null : user.getRealName();
        if (StringUtils.hasText(name)) {
            return name;
        }
        DataScopeContext context = DataScopeContext.current();
        return context != null && StringUtils.hasText(context.getUsername()) ? context.getUsername() : "system";
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime parseStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 10 ? LocalDate.parse(text).atStartOfDay() : LocalDateTime.parse(text, DATE_TIME);
    }

    private LocalDateTime parseEnd(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 10 ? LocalDate.parse(text).atTime(23, 59, 59) : LocalDateTime.parse(text, DATE_TIME);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }
}
