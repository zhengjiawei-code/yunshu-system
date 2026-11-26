package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.DepartRequest;
import com.yunshu.modules.transport.dto.ReasonRequest;
import com.yunshu.modules.transport.dto.TripVehicleRequest;
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
import com.yunshu.modules.transport.vo.TripActionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 车辆保存与车次状态流转（#10/#13/#14/#17）。
 * 发车 20→30、运单 70→80、配载单元 1→2、子车次 10→20；取消发车全量回退；关闭释放配载关系。
 */
@Service
@RequiredArgsConstructor
public class TripDepartService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TripService tripService;
    private final TripFeeService tripFeeService;
    private final BizTripMapper tripMapper;
    private final BizTripDestMapper destMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizWaybillMapper waybillMapper;
    private final SysVehicleMapper vehicleMapper;
    private final SysDriverMapper driverMapper;
    private final SysUserMapper userMapper;
    private final UserOrgService userOrgService;
    private final TripSummaryService summaryService;
    private final TripLogService tripLogService;

    /* ---------------- #10 车辆/司机 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void saveVehicle(Long tripId, TripVehicleRequest request) {
        BizTrip existing = tripService.requireVisible(tripId);
        requireEditable(existing, "发车后车辆信息已锁定，不能修改");
        UserOrgService.UserOrg org = userOrgService.current();
        Set<Long> scope = userOrgService.manageDeptIds(org);
        String operatorName = operatorName(org.userId());

        UpdateWrapper<BizTrip> update = new UpdateWrapper<>();
        update.eq("id", tripId)
                .set("update_by", org.userId())
                .set("update_by_name", operatorName);
        if (request.getVehicleId() == null) {
            update.set("vehicle_id", null)
                    .set("plate_no", trim(request.getPlateNo()))
                    .set("vehicle_type", trim(request.getVehicleType()))
                    .set("vehicle_load", request.getVehicleLoad())
                    .set("vehicle_volume", request.getVehicleVolume());
        } else {
            SysVehicle vehicle = vehicleMapper.selectById(request.getVehicleId());
            if (vehicle == null || !scope.contains(vehicle.getDeptId()) || !Boolean.TRUE.equals(vehicle.getStatus())) {
                throw new BizException(403, "无权使用该车辆");
            }
            update.set("vehicle_id", vehicle.getId())
                    .set("plate_no", vehicle.getPlateNo())
                    .set("vehicle_type", vehicle.getVehicleType())
                    .set("vehicle_load", vehicle.getVehicleLoad())
                    .set("vehicle_volume", vehicle.getVehicleVolume());
        }
        if (request.getDriverId() == null) {
            update.set("driver_id", null)
                    .set("driver_name", trim(request.getDriverName()))
                    .set("driver_phone", trim(request.getDriverPhone()));
        } else {
            SysDriver driver = driverMapper.selectById(request.getDriverId());
            if (driver == null || !scope.contains(driver.getDeptId()) || !Boolean.TRUE.equals(driver.getStatus())) {
                throw new BizException(403, "无权使用该司机");
            }
            update.set("driver_id", driver.getId())
                    .set("driver_name", driver.getDriverName())
                    .set("driver_phone", driver.getDriverPhone());
        }
        tripMapper.update(null, update);

        tripLogService.record(tripId, existing.getTripNo(), 11, null, existing.getDeptId(),
                org.userId(), operatorName, vehicleSnapshot(existing),
                vehicleSnapshot(tripMapper.selectById(tripId)), "车辆信息变更");
    }

    /* ---------------- #13 发车 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TripActionVO depart(Long tripId, DepartRequest request) {
        tripService.requireVisible(tripId);
        BizTrip trip = tripMapper.selectByIdForUpdate(tripId);
        if (trip == null) {
            throw new BizException(404, "车次不存在");
        }
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException(400, "仅装车配载状态可发车");
        }
        List<BizTripWaybill> rows = tripFeeService.rows(tripId);
        if (rows.isEmpty()) {
            throw new BizException(400, "至少需要1个配载单元才能发车");
        }
        if (!StringUtils.hasText(trip.getPlateNo()) || !StringUtils.hasText(trip.getDriverName())) {
            throw new BizException(400, "请先完善车辆与司机信息");
        }
        tripFeeService.assertReadyForDepart(trip, rows);

        UserOrgService.UserOrg org = userOrgService.current();
        String operatorName = operatorName(org.userId());
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> before = statusSnapshot(trip, rows);

        tripMapper.update(null, new UpdateWrapper<BizTrip>()
                .eq("id", tripId)
                .set("trip_status", TripEnums.TripStatus.IN_TRANSIT)
                .set("depart_time", now)
                .set("depart_by", org.userId())
                .set("depart_by_name", operatorName)
                .set("plan_arrive_time", request.getPlanArriveTime())
                .set("update_by", org.userId())
                .set("update_by_name", operatorName));

        int units = tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                .eq("trip_id", tripId)
                .eq("load_status", TripEnums.LoadStatus.LOADED)
                .set("load_status", TripEnums.LoadStatus.DEPARTED)
                .set("update_by", org.userId()));
        int waybills = 0;
        for (BizTripWaybill row : rows) {
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", row.getWaybillId())
                    .eq("status", 70)
                    .set("status", 80)
                    .set("load_status", TripEnums.LoadStatus.DEPARTED)
                    .set("update_by", org.userId()));
            if (updated == 0) {
                throw new BizException(409, "运单状态已变化，请刷新：" + row.getWaybillNo());
            }
            waybills += updated;
        }
        int dests = destMapper.update(null, new UpdateWrapper<BizTripDest>()
                .eq("trip_id", tripId)
                .eq("sub_status", TripEnums.SubStatus.PENDING)
                .set("sub_status", TripEnums.SubStatus.IN_TRANSIT)
                .set("update_by", org.userId()));

        tripLogService.record(tripId, trip.getTripNo(), 5, null, trip.getDeptId(),
                org.userId(), operatorName, before, statusSnapshot(tripMapper.selectById(tripId),
                        tripFeeService.rows(tripId)), "发车");

        TripActionVO vo = new TripActionVO();
        vo.setTripId(tripId);
        vo.setTripStatus(TripEnums.TripStatus.IN_TRANSIT);
        vo.setTripStatusName(TripEnums.TripStatus.name(TripEnums.TripStatus.IN_TRANSIT));
        vo.setAffectedUnits(units);
        vo.setAffectedWaybills(waybills);
        vo.setAffectedDests(dests);
        vo.setDepartTime(now.format(DATE_TIME));
        return vo;
    }

    /* ---------------- #14 取消发车 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TripActionVO cancelDepart(Long tripId, ReasonRequest request) {
        tripService.requireVisible(tripId);
        BizTrip trip = tripMapper.selectByIdForUpdate(tripId);
        if (trip == null) {
            throw new BizException(404, "车次不存在");
        }
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.IN_TRANSIT)) {
            throw new BizException(400, "仅车辆在途状态可取消发车");
        }
        long arrived = destMapper.selectCount(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId)
                .ge(BizTripDest::getSubStatus, TripEnums.SubStatus.ARRIVED));
        if (arrived > 0) {
            throw new BizException(400, "已有子车次到站，不能取消发车");
        }

        UserOrgService.UserOrg org = userOrgService.current();
        String operatorName = operatorName(org.userId());
        List<BizTripWaybill> rows = tripFeeService.rows(tripId);
        Map<String, Object> before = statusSnapshot(trip, rows);

        tripMapper.update(null, new UpdateWrapper<BizTrip>()
                .eq("id", tripId)
                .set("trip_status", TripEnums.TripStatus.LOADING)
                .set("depart_time", null)
                .set("depart_by", null)
                .set("depart_by_name", null)
                .set("update_by", org.userId())
                .set("update_by_name", operatorName));

        int units = tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                .eq("trip_id", tripId)
                .eq("load_status", TripEnums.LoadStatus.DEPARTED)
                .set("load_status", TripEnums.LoadStatus.LOADED)
                .set("update_by", org.userId()));
        int waybills = 0;
        for (BizTripWaybill row : rows) {
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", row.getWaybillId())
                    .eq("status", 80)
                    .set("status", 70)
                    .set("load_status", TripEnums.LoadStatus.LOADED)
                    .set("update_by", org.userId()));
            waybills += updated;
        }
        int dests = destMapper.update(null, new UpdateWrapper<BizTripDest>()
                .eq("trip_id", tripId)
                .eq("sub_status", TripEnums.SubStatus.IN_TRANSIT)
                .set("sub_status", TripEnums.SubStatus.PENDING)
                .set("update_by", org.userId()));

        tripLogService.record(tripId, trip.getTripNo(), 6, null, trip.getDeptId(),
                org.userId(), operatorName, before, statusSnapshot(tripMapper.selectById(tripId),
                        tripFeeService.rows(tripId)), request.getReason());

        TripActionVO vo = new TripActionVO();
        vo.setTripId(tripId);
        vo.setTripStatus(TripEnums.TripStatus.LOADING);
        vo.setTripStatusName(TripEnums.TripStatus.name(TripEnums.TripStatus.LOADING));
        vo.setAffectedUnits(units);
        vo.setAffectedWaybills(waybills);
        vo.setAffectedDests(dests);
        return vo;
    }

    /* ---------------- #17 关闭 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public TripActionVO close(Long tripId, ReasonRequest request) {
        tripService.requireVisible(tripId);
        BizTrip trip = tripMapper.selectByIdForUpdate(tripId);
        if (trip == null) {
            throw new BizException(404, "车次不存在");
        }
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException(400, "仅未发车车次可关闭");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        String operatorName = operatorName(org.userId());
        List<BizTripWaybill> rows = tripFeeService.rows(tripId);
        Map<String, Object> before = statusSnapshot(trip, rows);
        LocalDateTime now = LocalDateTime.now();

        int waybills = 0;
        for (BizTripWaybill row : rows) {
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", row.getWaybillId())
                    .set("status", row.getStatusBeforeLoad())
                    .set("load_status", TripEnums.LoadStatus.PENDING)
                    .set("update_by", org.userId()));
            waybills += updated;
        }
        int units = tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                .eq("trip_id", tripId)
                .set("deleted", 1)
                .set("update_by", org.userId()));

        tripMapper.update(null, new UpdateWrapper<BizTrip>()
                .eq("id", tripId)
                .set("trip_status", TripEnums.TripStatus.CLOSED)
                .set("close_reason", request.getReason())
                .set("close_time", now)
                .set("close_by", org.userId())
                .set("close_by_name", operatorName)
                .set("update_by", org.userId())
                .set("update_by_name", operatorName));
        summaryService.recalc(tripId);
        tripFeeService.reallocateIfConfigured(tripId);

        BizTrip after = tripMapper.selectById(tripId);
        tripLogService.record(tripId, trip.getTripNo(), 9, null, trip.getDeptId(),
                org.userId(), operatorName, before, statusSnapshot(after, List.of()), request.getReason());

        TripActionVO vo = new TripActionVO();
        vo.setTripId(tripId);
        vo.setTripStatus(TripEnums.TripStatus.CLOSED);
        vo.setTripStatusName(TripEnums.TripStatus.name(TripEnums.TripStatus.CLOSED));
        vo.setAffectedUnits(units);
        vo.setAffectedWaybills(waybills);
        vo.setAffectedDests(0);
        return vo;
    }

    /* ---------------- 工具 ---------------- */

    private void requireEditable(BizTrip trip, String message) {
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException(400, message);
        }
    }

    private Map<String, Object> vehicleSnapshot(BizTrip trip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vehicleId", trip.getVehicleId());
        map.put("plateNo", trip.getPlateNo());
        map.put("vehicleType", trip.getVehicleType());
        map.put("vehicleLoad", trip.getVehicleLoad());
        map.put("vehicleVolume", trip.getVehicleVolume());
        map.put("driverId", trip.getDriverId());
        map.put("driverName", trip.getDriverName());
        map.put("driverPhone", trip.getDriverPhone());
        return map;
    }

    private Map<String, Object> statusSnapshot(BizTrip trip, List<BizTripWaybill> rows) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tripStatus", trip.getTripStatus());
        map.put("departTime", trip.getDepartTime() == null ? null : trip.getDepartTime().format(DATE_TIME));
        map.put("unitCount", rows.size());
        map.put("waybillNos", rows.stream().map(BizTripWaybill::getWaybillNo).toList());
        return map;
    }

    private String operatorName(Long userId) {
        if (userId == null) {
            return "system";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getRealName()) ? "system" : user.getRealName();
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
