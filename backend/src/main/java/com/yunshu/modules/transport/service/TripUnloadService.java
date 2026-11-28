package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.constant.WaybillEnums;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.ArrivalFeeRequest;
import com.yunshu.modules.transport.dto.ArriveRequest;
import com.yunshu.modules.transport.dto.CancelUnloadRequest;
import com.yunshu.modules.transport.dto.UnloadRequest;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.entity.BizTripFee;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.entity.SysFeeType;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.mapper.BizTripFeeMapper;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import com.yunshu.modules.transport.vo.ArrivalFeeVO;
import com.yunshu.modules.transport.vo.DestActionVO;
import com.yunshu.modules.transport.vo.UnloadPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 到站卸车（#33/#34/#35/#36/#36a/#15a/#16a）。
 * 全部口径排除 delivery_method=3 大车直送；到站/卸车按 D9 推进状态与库存；到站费用只摊本子车次非大车直送单元。
 */
@Service
@RequiredArgsConstructor
public class TripUnloadService {

    private static final int TRUCK_DIRECT = 3;
    private static final int DEPT_TYPE_HUB = 4;
    private static final int DEPT_TYPE_TERMINAL = 5;
    private static final String FEE_UNLOAD = "FEE0019";
    private static final String FEE_OTHER = "FEE0020";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TripService tripService;
    private final TripFeeService feeService;
    private final TripSummaryService summaryService;
    private final TripLogService tripLogService;
    private final BizTripMapper tripMapper;
    private final BizTripDestMapper destMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizTripFeeMapper tripFeeMapper;
    private final BizWaybillMapper waybillMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final UserOrgService userOrgService;

    private record Scope(BizTrip trip, BizTripDest dest, SysDept destDept, UserOrgService.UserOrg org) {
    }

    /* ---------------- #33 卸车页 ---------------- */

    @DS("slave")
    public UnloadPageVO page(Long tripId, Long destId, String tab, String keyword,
                             Integer page, Integer pageSize) {
        Scope scope = requireScope(tripId, destId);
        BizTrip trip = scope.trip();
        BizTripDest dest = scope.dest();
        int deptType = scope.destDept() == null || scope.destDept().getDeptType() == null
                ? 0 : scope.destDept().getDeptType();

        List<BizTripWaybill> rows = destRows(tripId, destId);
        Map<Long, BizWaybill> waybills = waybillMap(rows);
        List<BizTripWaybill> units = new ArrayList<>();
        for (BizTripWaybill row : rows) {
            BizWaybill waybill = waybills.get(row.getWaybillId());
            if (waybill == null || Objects.equals(waybill.getDeliveryMethod(), TRUCK_DIRECT)) {
                continue;
            }
            units.add(row);
        }

        int pending = 0;
        int done = 0;
        for (BizTripWaybill unit : units) {
            if (isDone(waybills.get(unit.getWaybillId()))) {
                done++;
            } else {
                pending++;
            }
        }

        boolean readonly = Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                || Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)
                || Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED);
        boolean feeEditable = Objects.equals(trip.getTripCategory(), 1) && deptType == DEPT_TYPE_TERMINAL
                && (Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.ARRIVED)
                || Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED))
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED)
                && !units.isEmpty();
        boolean canCancelArrive = Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.ARRIVED)
                && !readonly && done == 0;
        boolean canCancelUnload = (Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED) || done > 0)
                && !readonly && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED);

        UnloadPageVO vo = new UnloadPageVO();
        UnloadPageVO.TripInfo tripInfo = new UnloadPageVO.TripInfo();
        tripInfo.setTripId(trip.getId());
        tripInfo.setTripNo(trip.getTripNo());
        tripInfo.setTripName(trip.getTripName());
        tripInfo.setTripStatus(trip.getTripStatus());
        tripInfo.setTripStatusName(TripEnums.TripStatus.name(trip.getTripStatus()));
        tripInfo.setTripCategory(trip.getTripCategory());
        tripInfo.setPlateNo(trip.getPlateNo());
        tripInfo.setDriverName(trip.getDriverName());
        tripInfo.setOriginDeptName(trip.getOriginDeptName());
        vo.setTrip(tripInfo);

        UnloadPageVO.DestInfo destInfo = new UnloadPageVO.DestInfo();
        destInfo.setDestId(dest.getId());
        destInfo.setSubTripNo(dest.getSubTripNo());
        destInfo.setDestDeptId(dest.getDestDeptId());
        destInfo.setDestDeptName(dest.getDestDeptName());
        destInfo.setSubStatus(dest.getSubStatus());
        destInfo.setArriveTime(dest.getArriveTime());
        destInfo.setUnloadTime(dest.getUnloadTime());
        vo.setDest(destInfo);

        UnloadPageVO.Counts counts = new UnloadPageVO.Counts();
        counts.setAll(units.size());
        counts.setPending(pending);
        counts.setDone(done);
        vo.setCounts(counts);

        UnloadPageVO.Summary summary = new UnloadPageVO.Summary();
        summary.setUnitCount(units.size());
        int quantity = 0;
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        for (BizTripWaybill unit : units) {
            quantity += unit.getQuantity() == null ? 0 : unit.getQuantity();
            weight = weight.add(nvl(unit.getWeight()));
            volume = volume.add(nvl(unit.getVolume()));
        }
        summary.setQuantity(quantity);
        summary.setWeight(weight);
        summary.setVolume(volume);
        vo.setSummary(summary);

        String text = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        List<UnloadPageVO.Record> records = new ArrayList<>();
        for (BizTripWaybill unit : units) {
            BizWaybill waybill = waybills.get(unit.getWaybillId());
            boolean unloaded = isDone(waybill);
            if ("pending".equalsIgnoreCase(tab) && unloaded) {
                continue;
            }
            if ("done".equalsIgnoreCase(tab) && !unloaded) {
                continue;
            }
            if (text != null) {
                boolean match = contains(unit.getWaybillNo(), text) || contains(unit.getParentNo(), text)
                        || contains(unit.getReceiverName(), text) || contains(unit.getGoodsName(), text);
                if (!match) {
                    continue;
                }
            }
            UnloadPageVO.Record record = new UnloadPageVO.Record();
            record.setTripWaybillId(unit.getId());
            record.setWaybillId(unit.getWaybillId());
            record.setWaybillNo(unit.getWaybillNo());
            record.setParentNo(unit.getParentNo());
            record.setIsChild(unit.getIsChild());
            record.setStatus(waybill.getStatus());
            record.setStatusName(WaybillEnums.Status.labelOf(waybill.getStatus()));
            record.setDeliveryMethod(waybill.getDeliveryMethod());
            record.setDeliveryMethodName(WaybillEnums.DeliveryMethod.labelOf(waybill.getDeliveryMethod()));
            record.setDestName(waybill.getDestName());
            record.setGoodsName(waybill.getGoodsName());
            record.setQuantity(waybill.getQuantity());
            record.setWeight(waybill.getWeight());
            record.setVolume(waybill.getVolume());
            record.setFreightFee(waybill.getFreightFee());
            record.setDeliveryFee(waybill.getDeliveryFee());
            record.setCollectFee(waybill.getCollectFee());
            record.setReceiverName(waybill.getReceiverName());
            record.setReceiverPhone(waybill.getReceiverPhone());
            record.setCreateTime(waybill.getCreateTime());
            record.setLoadStatus(unit.getLoadStatus());
            record.setUnloaded(unloaded);
            records.add(record);
        }
        records.sort(Comparator.comparing(UnloadPageVO.Record::getTripWaybillId));

        int pageNo = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int from = Math.min((pageNo - 1) * size, records.size());
        int to = Math.min(from + size, records.size());
        vo.setRecords(records.subList(from, to));
        vo.setTotal(records.size());
        vo.setPage(pageNo);
        vo.setPageSize(size);

        vo.setFeeEditable(feeEditable);
        vo.setUnloadFeeValid(!Boolean.FALSE.equals(dest.getArrivalUnloadFeeValid()));
        vo.setOtherFeeValid(!Boolean.FALSE.equals(dest.getArrivalOtherFeeValid()));
        vo.setArrivalUnloadFee(nvl(dest.getArrivalUnloadFee()));
        vo.setArrivalOtherFee(nvl(dest.getArrivalOtherFee()));
        // 到站付运输费按目的网点展示（V36 起按网点录入）
        vo.setArrivalTransportFee(feeService.arrivalTransportFeeOf(trip.getId(), dest.getId()));
        vo.setAllocMethod(trip.getAllocMethod());
        vo.setAllocMethodName(allocMethodName(trip.getAllocMethod()));
        vo.setCanCancelArrive(canCancelArrive);
        vo.setCanCancelUnload(canCancelUnload);
        vo.setReadonly(readonly);
        return vo;
    }

    /* ---------------- #34 到站 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public DestActionVO arrive(Long tripId, Long destId, ArriveRequest request) {
        Scope scope = requireScope(tripId, destId);
        BizTrip trip = scope.trip();
        BizTripDest dest = scope.dest();
        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED)) {
            throw new BizException(400, "车次已关闭，不能到站");
        }
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.IN_TRANSIT)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PART_ARRIVED)) {
            throw new BizException(400, "仅车辆在途状态可确认到站");
        }
        if (!Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.IN_TRANSIT)
                && !Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.PENDING)) {
            throw new BizException(400, "该子车次已到站，不能重复到站");
        }
        boolean hub = isHub(scope.destDept());
        LocalDateTime arriveTime = request != null && request.getArriveTime() != null
                ? request.getArriveTime() : LocalDateTime.now();
        int target = hub ? WaybillEnums.Status.TRANSIT_ARRIVED.getCode()
                : WaybillEnums.Status.TERMINAL_ARRIVED.getCode();

        UserOrgService.UserOrg org = scope.org();
        String operatorName = operatorName(org.userId());
        List<BizTripWaybill> units = activeUnits(tripId, destId);
        int affected = 0;
        for (BizTripWaybill unit : units) {
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", unit.getWaybillId())
                    .eq("status", WaybillEnums.Status.TRANSIT_DEPARTED.getCode())
                    .set("status", target)
                    .set("load_status", TripEnums.LoadStatus.ARRIVED)
                    .set("update_by", org.userId()));
            if (updated == 0) {
                throw new BizException(409, "运单状态已变化，请刷新：" + unit.getWaybillNo());
            }
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", unit.getId())
                    .set("load_status", TripEnums.LoadStatus.ARRIVED)
                    .set("update_by", org.userId()));
            affected += updated;
        }

        destMapper.update(null, new UpdateWrapper<BizTripDest>()
                .eq("id", destId)
                .eq("sub_status", dest.getSubStatus())
                .set("sub_status", TripEnums.SubStatus.ARRIVED)
                .set("arrive_time", arriveTime)
                .set("arrive_by", org.userId())
                .set("arrive_by_name", operatorName)
                .set("update_by", org.userId()));

        int tripStatus = refreshTripArriveStatus(tripId, arriveTime);
        tripLogService.record(tripId, trip.getTripNo(), 7, destId, trip.getDeptId(),
                org.userId(), operatorName,
                Map.of("subStatus", dest.getSubStatus()), Map.of("subStatus", TripEnums.SubStatus.ARRIVED,
                        "arriveTime", arriveTime.format(DATE_TIME), "waybills", affected), "子车次到站");

        return action(tripId, destId, tripStatus, TripEnums.SubStatus.ARRIVED, affected, units.size(), 0);
    }

    /* ---------------- #35 卸车 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public DestActionVO unload(Long tripId, Long destId, UnloadRequest request) {
        Scope scope = requireScope(tripId, destId);
        BizTrip trip = scope.trip();
        BizTripDest dest = scope.dest();
        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED)) {
            throw new BizException(400, "车次已关闭，不能卸车");
        }
        if (!Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.ARRIVED)
                && !Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED)) {
            throw new BizException(400, "子车次未到站，不能卸车");
        }
        boolean hub = isHub(scope.destDept());
        List<BizTripWaybill> units = activeUnits(tripId, destId);
        Map<Long, BizTripWaybill> unitByWaybill = new HashMap<>();
        units.forEach(unit -> unitByWaybill.put(unit.getWaybillId(), unit));

        List<BizTripWaybill> targets = new ArrayList<>();
        if (request == null || request.getWaybillIds() == null || request.getWaybillIds().isEmpty()) {
            if (!units.isEmpty()) {
                boolean hasPending = units.stream()
                        .anyMatch(unit -> !isDone(waybillMapper.selectById(unit.getWaybillId())));
                if (hasPending) {
                    throw new BizException(400, "请选择要卸车的运单");
                }
            }
        } else {
            Set<Long> seen = new HashSet<>();
            for (Long waybillId : request.getWaybillIds()) {
                BizTripWaybill unit = unitByWaybill.get(waybillId);
                if (unit == null) {
                    throw new BizException(400, "运单不属于本子车次或为大车直送：" + waybillId);
                }
                if (!seen.add(waybillId)) {
                    continue;
                }
                BizWaybill waybill = waybillMapper.selectById(waybillId);
                if (waybill == null || isDone(waybill)) {
                    throw new BizException(400, "运单已卸车或不可卸：" + waybillId);
                }
                targets.add(unit);
            }
        }

        UserOrgService.UserOrg org = scope.org();
        String operatorName = operatorName(org.userId());
        LocalDateTime now = LocalDateTime.now();
        int affected = 0;
        for (BizTripWaybill unit : targets) {
            BizWaybill waybill = waybillMapper.selectById(unit.getWaybillId());
            int target = unloadTargetStatus(waybill, hub);
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", waybill.getId())
                    .in("status", WaybillEnums.Status.TERMINAL_ARRIVED.getCode(),
                            WaybillEnums.Status.TRANSIT_ARRIVED.getCode())
                    .set("status", target)
                    .set("load_status", hub ? TripEnums.LoadStatus.PENDING : TripEnums.LoadStatus.UNLOADED)
                    .set("current_hub_id", dest.getDestDeptId())
                    .set("current_hub_name", dest.getDestDeptName())
                    .set("current_node_seq", unit.getRouteNodeSeq())
                    .set("inventory_time", now)
                    .set("update_by", org.userId()));
            if (updated == 0) {
                throw new BizException(409, "运单状态已变化，请刷新：" + unit.getWaybillNo());
            }
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", unit.getId())
                    .set("load_status", hub ? TripEnums.LoadStatus.PENDING : TripEnums.LoadStatus.UNLOADED)
                    .set("update_by", org.userId()));
            affected += updated;
        }

        boolean allDone = true;
        for (BizTripWaybill unit : units) {
            if (!isDone(waybillMapper.selectById(unit.getWaybillId()))) {
                allDone = false;
                break;
            }
        }
        int destStatus = dest.getSubStatus();
        if (allDone && !Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED)) {
            destMapper.update(null, new UpdateWrapper<BizTripDest>()
                    .eq("id", destId)
                    .set("sub_status", TripEnums.SubStatus.UNLOADED)
                    .set("unload_time", now)
                    .set("unload_by", org.userId())
                    .set("unload_by_name", operatorName)
                    .set("update_by", org.userId()));
            destStatus = TripEnums.SubStatus.UNLOADED;
        }
        int tripStatus = refreshTripUnloadStatus(tripId);
        String reason = targets.isEmpty() ? "0 票卸车" : "卸车";
        tripLogService.record(tripId, trip.getTripNo(), 8, destId, trip.getDeptId(),
                org.userId(), operatorName,
                Map.of("subStatus", dest.getSubStatus()), Map.of("subStatus", destStatus,
                        "waybills", affected, "zeroTicket", targets.isEmpty()), reason);

        return action(tripId, destId, tripStatus, destStatus, affected, units.size(), 0);
    }

    /* ---------------- #36 到站费用保存 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public ArrivalFeeVO saveArrivalFees(Long tripId, Long destId, ArrivalFeeRequest request) {
        Scope scope = requireScope(tripId, destId);
        requireFeeEditable(scope);
        UserOrgService.UserOrg org = scope.org();
        String operatorName = operatorName(org.userId());
        BigDecimal unloadFee = money(request.getUnloadFee());
        BigDecimal otherFee = money(request.getOtherFee());
        Map<String, Object> before = new LinkedHashMap<>(feeService.savedDestAmounts(tripId, destId));

        Map<String, SysFeeType> types = feeService.destLevelTypes();
        SysFeeType unloadType = types.get(FEE_UNLOAD);
        SysFeeType otherType = types.get(FEE_OTHER);
        if (unloadType == null || otherType == null) {
            throw new BizException(400, "到站费用项未配置（FEE0019/FEE0020）");
        }
        tripFeeMapper.upsertDestLevel(tripId, destId, unloadType.getFeeCode(), unloadType.getFeeName(),
                unloadFee, unloadType.getProfitEnabled(), unloadType.getProfitDirection(),
                unloadType.getSortOrder(), scope.trip().getDeptId(), org.userId());
        tripFeeMapper.upsertDestLevel(tripId, destId, otherType.getFeeCode(), otherType.getFeeName(),
                otherFee, otherType.getProfitEnabled(), otherType.getProfitDirection(),
                otherType.getSortOrder(), scope.trip().getDeptId(), org.userId());

        destMapper.update(null, new UpdateWrapper<BizTripDest>()
                .eq("id", destId)
                .set("arrival_unload_fee", unloadFee)
                .set("arrival_unload_fee_valid", true)
                .set("arrival_other_fee", otherFee)
                .set("arrival_other_fee_valid", true)
                .set("update_by", org.userId()));

        feeService.rebuildAllocation(tripId);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put(FEE_UNLOAD, unloadFee);
        after.put(FEE_OTHER, otherFee);
        tripLogService.record(tripId, scope.trip().getTripNo(), 12, destId, scope.trip().getDeptId(),
                org.userId(), operatorName, before, after, "到站费用变更");

        return buildArrivalFeeVO(scope, unloadFee, otherFee, true, true);
    }

    /* ---------------- #36a 到站费用预览 ---------------- */

    @DS("slave")
    public ArrivalFeeVO previewArrivalFees(Long tripId, Long destId, BigDecimal unloadFee, BigDecimal otherFee) {
        Scope scope = requireScope(tripId, destId);
        Map<String, BigDecimal> saved = feeService.savedDestAmounts(tripId, destId);
        BigDecimal unload = unloadFee == null ? saved.getOrDefault(FEE_UNLOAD, BigDecimal.ZERO) : unloadFee;
        BigDecimal other = otherFee == null ? saved.getOrDefault(FEE_OTHER, BigDecimal.ZERO) : otherFee;
        requireFeeEditable(scope);
        return buildArrivalFeeVO(scope, money(unload), money(other), true, true);
    }

    /* ---------------- #15a 取消到站 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public DestActionVO cancelArrive(Long tripId, Long destId, String reason) {
        Scope scope = requireScope(tripId, destId);
        BizTrip trip = scope.trip();
        BizTripDest dest = scope.dest();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(400, "操作原因不能为空");
        }
        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED)) {
            throw new BizException(400, "车次已关闭，不能取消到站");
        }
        if (!Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.ARRIVED)) {
            throw new BizException(400, "仅已到站且未卸完的子车次可取消到站");
        }
        List<BizTripWaybill> units = activeUnits(tripId, destId);
        for (BizTripWaybill unit : units) {
            BizWaybill waybill = waybillMapper.selectById(unit.getWaybillId());
            if (waybill != null && isDone(waybill)) {
                throw new BizException(400, "子车次已有卸车运单，不能取消到站");
            }
        }

        UserOrgService.UserOrg org = scope.org();
        String operatorName = operatorName(org.userId());
        int affected = 0;
        for (BizTripWaybill unit : units) {
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", unit.getWaybillId())
                    .in("status", WaybillEnums.Status.TERMINAL_ARRIVED.getCode(),
                            WaybillEnums.Status.TRANSIT_ARRIVED.getCode())
                    .set("status", WaybillEnums.Status.TRANSIT_DEPARTED.getCode())
                    .set("load_status", TripEnums.LoadStatus.DEPARTED)
                    .set("update_by", org.userId()));
            if (updated == 0) {
                throw new BizException(409, "运单状态已变化，请刷新：" + unit.getWaybillNo());
            }
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", unit.getId())
                    .set("load_status", TripEnums.LoadStatus.DEPARTED)
                    .set("update_by", org.userId()));
            affected += updated;
        }
        destMapper.update(null, new UpdateWrapper<BizTripDest>()
                .eq("id", destId)
                .set("sub_status", TripEnums.SubStatus.IN_TRANSIT)
                .set("arrive_time", null)
                .set("arrive_by", null)
                .set("arrive_by_name", null)
                .set("update_by", org.userId()));

        int invalidFeeCount = invalidateDestFees(tripId, destId, "取消到站失效");
        int tripStatus = refreshTripCancelArriveStatus(tripId);
        feeService.rebuildAllocation(tripId);
        tripLogService.record(tripId, trip.getTripNo(), 16, destId, trip.getDeptId(),
                org.userId(), operatorName,
                Map.of("subStatus", TripEnums.SubStatus.ARRIVED),
                Map.of("subStatus", TripEnums.SubStatus.IN_TRANSIT, "waybills", affected,
                        "invalidFeeCount", invalidFeeCount), reason);

        return action(tripId, destId, tripStatus, TripEnums.SubStatus.IN_TRANSIT, affected, units.size(), invalidFeeCount);
    }

    /* ---------------- #16a 取消卸车 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public DestActionVO cancelUnload(Long tripId, Long destId, CancelUnloadRequest request) {
        Scope scope = requireScope(tripId, destId);
        BizTrip trip = scope.trip();
        BizTripDest dest = scope.dest();
        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED)) {
            throw new BizException(400, "车次已关闭，不能取消卸车");
        }
        if (!Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.ARRIVED)
                && !Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED)) {
            throw new BizException(400, "子车次未到站，不能取消卸车");
        }
        boolean hub = isHub(scope.destDept());
        List<BizTripWaybill> units = activeUnits(tripId, destId);
        Map<Long, BizTripWaybill> unitByWaybill = new HashMap<>();
        units.forEach(unit -> unitByWaybill.put(unit.getWaybillId(), unit));

        UserOrgService.UserOrg org = scope.org();
        String operatorName = operatorName(org.userId());
        int affected = 0;
        for (Long waybillId : request.getWaybillIds()) {
            BizTripWaybill unit = unitByWaybill.get(waybillId);
            if (unit == null) {
                throw new BizException(400, "运单不属于本子车次或为大车直送：" + waybillId);
            }
            long downstream = tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                    .eq(BizTripWaybill::getWaybillId, waybillId)
                    .ne(BizTripWaybill::getTripId, tripId)
                    .in(BizTripWaybill::getLoadStatus, TripEnums.LoadStatus.LOADED,
                            TripEnums.LoadStatus.DEPARTED, TripEnums.LoadStatus.ARRIVED));
            if (downstream > 0) {
                throw new BizException(409, "运单已被下一车次配载，不能取消卸车：" + unit.getWaybillNo());
            }
            int prevSeq = unit.getRouteNodeSeq() == null || unit.getRouteNodeSeq() <= 1
                    ? 1 : unit.getRouteNodeSeq() - 1;
            int updated = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", waybillId)
                    .in("status", WaybillEnums.Status.ARRIVED_WAIT_PICKUP.getCode(),
                            WaybillEnums.Status.ARRIVED_WAIT_DELIVERY.getCode(),
                            WaybillEnums.Status.ARRIVED_WAIT_TRANSFER.getCode(),
                            WaybillEnums.Status.TRANSIT_UNLOADED.getCode())
                    .in("load_status", TripEnums.LoadStatus.UNLOADED, TripEnums.LoadStatus.PENDING)
                    .set("status", hub ? WaybillEnums.Status.TRANSIT_ARRIVED.getCode()
                            : WaybillEnums.Status.TERMINAL_ARRIVED.getCode())
                    .set("load_status", TripEnums.LoadStatus.ARRIVED)
                    .set("current_hub_id", trip.getOriginDeptId())
                    .set("current_hub_name", trip.getOriginDeptName())
                    .set("current_node_seq", prevSeq)
                    .set("inventory_time", unit.getLoadTime())
                    .set("update_by", org.userId()));
            if (updated == 0) {
                throw new BizException(409, "库存或状态已变化，请刷新后重试：" + unit.getWaybillNo());
            }
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", unit.getId())
                    .set("load_status", TripEnums.LoadStatus.ARRIVED)
                    .set("update_by", org.userId()));
            affected += updated;
        }

        boolean remainDone = false;
        for (BizTripWaybill unit : units) {
            if (isDone(waybillMapper.selectById(unit.getWaybillId()))) {
                remainDone = true;
                break;
            }
        }
        int destStatus = dest.getSubStatus();
        if (!remainDone) {
            destMapper.update(null, new UpdateWrapper<BizTripDest>()
                    .eq("id", destId)
                    .set("sub_status", TripEnums.SubStatus.ARRIVED)
                    .set("unload_time", null)
                    .set("unload_by", null)
                    .set("unload_by_name", null)
                    .set("update_by", org.userId()));
            destStatus = TripEnums.SubStatus.ARRIVED;
        }
        int invalidFeeCount = invalidateDestFees(tripId, destId, "取消卸车失效");
        int tripStatus = refreshTripUnloadStatus(tripId);
        feeService.rebuildAllocation(tripId);
        tripLogService.record(tripId, trip.getTripNo(), 17, destId, trip.getDeptId(),
                org.userId(), operatorName,
                Map.of("subStatus", dest.getSubStatus()),
                Map.of("subStatus", destStatus, "waybills", affected,
                        "invalidFeeCount", invalidFeeCount, "originDeptId", trip.getOriginDeptId()), request.getReason());

        return action(tripId, destId, tripStatus, destStatus, affected, units.size(), invalidFeeCount);
    }

    /* ---------------- 内部 ---------------- */

    private Scope requireScope(Long tripId, Long destId) {
        BizTrip trip = tripService.requireVisible(tripId);
        BizTripDest dest = destMapper.selectById(destId);
        if (dest == null || !Objects.equals(dest.getTripId(), tripId) || Boolean.TRUE.equals(dest.getDeleted())) {
            throw new BizException(404, "子车次不存在");
        }
        UserOrgService.UserOrg org = userOrgService.current();
        if (!org.hq() && !Objects.equals(org.deptId(), dest.getDestDeptId())) {
            throw new BizException(403, "无权操作非本站子车次");
        }
        SysDept dept = dest.getDestDeptId() == null ? null : deptMapper.selectById(dest.getDestDeptId());
        return new Scope(trip, dest, dept, org);
    }

    private List<BizTripWaybill> destRows(Long tripId, Long destId) {
        return tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripId, tripId)
                .eq(BizTripWaybill::getTripDestId, destId)
                .orderByAsc(BizTripWaybill::getId));
    }

    /** 本子车次有效配载单元，排除大车直送 */
    private List<BizTripWaybill> activeUnits(Long tripId, Long destId) {
        List<BizTripWaybill> rows = destRows(tripId, destId);
        List<BizTripWaybill> units = new ArrayList<>();
        for (BizTripWaybill row : rows) {
            BizWaybill waybill = waybillMapper.selectById(row.getWaybillId());
            if (waybill == null || Objects.equals(waybill.getDeliveryMethod(), TRUCK_DIRECT)) {
                continue;
            }
            units.add(row);
        }
        return units;
    }

    private Map<Long, BizWaybill> waybillMap(List<BizTripWaybill> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(BizTripWaybill::getWaybillId).distinct().toList();
        Map<Long, BizWaybill> map = new HashMap<>();
        waybillMapper.selectBatchIds(ids).forEach(waybill -> map.put(waybill.getId(), waybill));
        return map;
    }

    private boolean isDone(BizWaybill waybill) {
        if (waybill == null || waybill.getStatus() == null) {
            return false;
        }
        int status = waybill.getStatus();
        return status == WaybillEnums.Status.ARRIVED_WAIT_PICKUP.getCode()
                || status == WaybillEnums.Status.ARRIVED_WAIT_DELIVERY.getCode()
                || status == WaybillEnums.Status.ARRIVED_WAIT_TRANSFER.getCode()
                || status == WaybillEnums.Status.TRANSIT_UNLOADED.getCode();
    }

    private int unloadTargetStatus(BizWaybill waybill, boolean hub) {
        if (hub) {
            return WaybillEnums.Status.TRANSIT_UNLOADED.getCode();
        }
        if (Objects.equals(waybill.getRouteEndType(), 2)) {
            return WaybillEnums.Status.ARRIVED_WAIT_TRANSFER.getCode();
        }
        if (Objects.equals(waybill.getDeliveryMethod(), WaybillEnums.DeliveryMethod.SELF_PICKUP.getCode())) {
            return WaybillEnums.Status.ARRIVED_WAIT_PICKUP.getCode();
        }
        return WaybillEnums.Status.ARRIVED_WAIT_DELIVERY.getCode();
    }

    private void requireFeeEditable(Scope scope) {
        BizTrip trip = scope.trip();
        BizTripDest dest = scope.dest();
        int deptType = scope.destDept() == null || scope.destDept().getDeptType() == null
                ? 0 : scope.destDept().getDeptType();
        if (!Objects.equals(trip.getTripCategory(), 1) || deptType != DEPT_TYPE_TERMINAL) {
            throw new BizException(400, "仅干线车次的末端网点可录入到站费用");
        }
        if (!Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.ARRIVED)
                && !Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED)) {
            throw new BizException(400, "到站后可录入到站费用");
        }
        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.CLOSED)) {
            throw new BizException(400, "车次已关闭，不能录入到站费用");
        }
        if (activeUnits(trip.getId(), dest.getId()).isEmpty()) {
            throw new BizException(400, "本子车次无末端卸车运单，不产生到站费用");
        }
    }

    private ArrivalFeeVO buildArrivalFeeVO(Scope scope, BigDecimal unloadFee, BigDecimal otherFee,
                                           boolean unloadValid, boolean otherValid) {
        BizTrip trip = scope.trip();
        List<BizTripWaybill> units = activeUnits(trip.getId(), scope.dest().getId());
        int method = trip.getAllocMethod() == null ? TripEnums.AllocMethod.CHARGE_WEIGHT : trip.getAllocMethod();
        List<FeeAllocator.Unit> allocUnits = new ArrayList<>();
        Map<Long, BigDecimal> chargeWeights = new HashMap<>();
        for (BizTripWaybill unit : units) {
            BigDecimal ratio = feeService.chargeRatioOf(unit);
            BigDecimal chargeWeight = feeService.chargeWeightOf(unit, ratio);
            chargeWeights.put(unit.getId(), chargeWeight);
            allocUnits.add(new FeeAllocator.Unit(unit.getId(), nvl(unit.getWeight()), nvl(unit.getVolume()), chargeWeight));
        }
        List<FeeAllocator.Result> unloadResults = FeeAllocator.allocate(unloadFee, allocUnits, method);
        List<FeeAllocator.Result> otherResults = FeeAllocator.allocate(otherFee, allocUnits, method);
        Map<Long, BigDecimal> unloadMap = new HashMap<>();
        Map<Long, BigDecimal> otherMap = new HashMap<>();
        unloadResults.forEach(result -> unloadMap.put(result.unitId(), result.amount()));
        otherResults.forEach(result -> otherMap.put(result.unitId(), result.amount()));

        ArrivalFeeVO vo = new ArrivalFeeVO();
        vo.setTripId(trip.getId());
        vo.setDestId(scope.dest().getId());
        vo.setUnloadFee(unloadFee);
        vo.setOtherFee(otherFee);
        vo.setTotal(unloadFee.add(otherFee));
        vo.setUnloadFeeValid(unloadValid);
        vo.setOtherFeeValid(otherValid);
        vo.setAllocMethod(method);
        vo.setAllocMethodName(allocMethodName(method));
        List<ArrivalFeeVO.Unit> rows = new ArrayList<>();
        for (BizTripWaybill unit : units) {
            ArrivalFeeVO.Unit row = new ArrivalFeeVO.Unit();
            row.setTripWaybillId(unit.getId());
            row.setWaybillNo(unit.getWaybillNo());
            row.setWeight(nvl(unit.getWeight()));
            row.setVolume(nvl(unit.getVolume()));
            row.setChargeWeight(chargeWeights.get(unit.getId()));
            row.setBasis(FeeAllocator.basis(new FeeAllocator.Unit(unit.getId(), nvl(unit.getWeight()),
                    nvl(unit.getVolume()), chargeWeights.get(unit.getId())), method));
            BigDecimal basisTotal = unloadResults.isEmpty() ? BigDecimal.ZERO : unloadResults.get(0).basisTotal();
            row.setRatio(basisTotal.signum() == 0 ? BigDecimal.ZERO
                    : row.getBasis().divide(basisTotal, 8, RoundingMode.HALF_UP));
            Map<String, BigDecimal> fees = new LinkedHashMap<>();
            fees.put(FEE_UNLOAD, unloadMap.getOrDefault(unit.getId(), BigDecimal.ZERO));
            fees.put(FEE_OTHER, otherMap.getOrDefault(unit.getId(), BigDecimal.ZERO));
            row.setFees(fees);
            row.setAllocatedFee(fees.get(FEE_UNLOAD).add(fees.get(FEE_OTHER)));
            rows.add(row);
        }
        vo.setUnits(rows);
        return vo;
    }

    private int invalidateDestFees(Long tripId, Long destId, String reason) {
        int updated = tripFeeMapper.update(null, new UpdateWrapper<BizTripFee>()
                .eq("trip_id", tripId)
                .eq("trip_dest_id", destId)
                .in("fee_code", FEE_UNLOAD, FEE_OTHER)
                .set("fee_status", 2)
                .set("invalid_reason", reason));
        destMapper.update(null, new UpdateWrapper<BizTripDest>()
                .eq("id", destId)
                .set("arrival_unload_fee_valid", false)
                .set("arrival_other_fee_valid", false));
        return updated;
    }

    private int refreshTripArriveStatus(Long tripId, LocalDateTime arriveTime) {
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId));
        long arrived = dests.stream().filter(dest -> dest.getSubStatus() != null
                && dest.getSubStatus() >= TripEnums.SubStatus.ARRIVED).count();
        int status = arrived == dests.size() ? TripEnums.TripStatus.ARRIVED : TripEnums.TripStatus.PART_ARRIVED;
        LocalDateTime maxArrive = dests.stream().map(BizTripDest::getArriveTime)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(arriveTime);
        tripMapper.update(null, new UpdateWrapper<BizTrip>()
                .eq("id", tripId)
                .set("trip_status", status)
                .set("actual_arrive_time", maxArrive));
        return status;
    }

    private int refreshTripCancelArriveStatus(Long tripId) {
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId));
        long arrived = dests.stream().filter(dest -> dest.getSubStatus() != null
                && dest.getSubStatus() >= TripEnums.SubStatus.ARRIVED).count();
        int status;
        if (arrived == 0) {
            status = TripEnums.TripStatus.IN_TRANSIT;
        } else if (arrived == dests.size()) {
            status = TripEnums.TripStatus.ARRIVED;
        } else {
            status = TripEnums.TripStatus.PART_ARRIVED;
        }
        LocalDateTime maxArrive = dests.stream().map(BizTripDest::getArriveTime)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        tripMapper.update(null, new UpdateWrapper<BizTrip>()
                .eq("id", tripId)
                .set("trip_status", status)
                .set("actual_arrive_time", maxArrive));
        return status;
    }

    private int refreshTripUnloadStatus(Long tripId) {
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId));
        long arrived = dests.stream().filter(dest -> dest.getSubStatus() != null
                && dest.getSubStatus() >= TripEnums.SubStatus.ARRIVED).count();
        // 完成卸车按配载单元实际状态判定，不能只看子车次状态（部分取消卸车后子车次仍为 40）
        long unloaded = 0;
        for (BizTripDest dest : dests) {
            List<BizTripWaybill> units = activeUnits(tripId, dest.getId());
            if (units.isEmpty()) {
                if (Objects.equals(dest.getSubStatus(), TripEnums.SubStatus.UNLOADED)) {
                    unloaded++;
                }
                continue;
            }
            boolean allDone = units.stream()
                    .allMatch(unit -> isDone(waybillMapper.selectById(unit.getWaybillId())));
            if (allDone) {
                unloaded++;
            }
        }
        int status;
        if (unloaded == dests.size()) {
            status = TripEnums.TripStatus.UNLOADED;
        } else if (arrived == 0) {
            status = TripEnums.TripStatus.IN_TRANSIT;
        } else if (arrived == dests.size()) {
            status = TripEnums.TripStatus.ARRIVED;
        } else {
            status = TripEnums.TripStatus.PART_ARRIVED;
        }
        tripMapper.update(null, new UpdateWrapper<BizTrip>()
                .eq("id", tripId)
                .set("trip_status", status));
        return status;
    }

    private DestActionVO action(Long tripId, Long destId, int tripStatus, int destStatus,
                                int waybills, int units, int invalidFeeCount) {
        DestActionVO vo = new DestActionVO();
        vo.setTripId(tripId);
        vo.setDestId(destId);
        vo.setTripStatus(tripStatus);
        vo.setTripStatusName(TripEnums.TripStatus.name(tripStatus));
        vo.setDestStatus(destStatus);
        vo.setAffectedWaybills(waybills);
        vo.setAffectedUnits(units);
        vo.setInvalidFeeCount(invalidFeeCount);
        return vo;
    }

    private boolean isHub(SysDept dept) {
        return dept != null && Objects.equals(dept.getDeptType(), DEPT_TYPE_HUB);
    }

    private String operatorName(Long userId) {
        if (userId == null) {
            return "system";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getRealName()) ? "system" : user.getRealName();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String allocMethodName(Integer method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case TripEnums.AllocMethod.WEIGHT -> "按重量";
            case TripEnums.AllocMethod.VOLUME -> "按体积";
            case TripEnums.AllocMethod.CHARGE_WEIGHT -> "按重泡比";
            default -> "未知";
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
