package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import com.yunshu.modules.operation.mapper.BizWaybillRouteNodeMapper;
import com.yunshu.modules.system.route.entity.SysRoute;
import com.yunshu.modules.system.route.mapper.SysRouteMapper;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.TripFeeSaveRequest;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripFee;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.entity.BizTripWaybillFee;
import com.yunshu.modules.transport.entity.SysConfig;
import com.yunshu.modules.transport.entity.SysFeeType;
import com.yunshu.modules.transport.mapper.BizTripFeeMapper;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillFeeMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import com.yunshu.modules.transport.mapper.SysConfigMapper;
import com.yunshu.modules.transport.mapper.SysFeeTypeMapper;
import com.yunshu.modules.transport.vo.FeePreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 车次级费用保存与分摊（#11/#12）：全车统一 alloc_method，逐票分摊到 biz_trip_waybill_fee。
 * 每项 Σ分摊 = 该项总额，尾差由最后一票吸收；票毛利 = 四类运费 − 返款 − 参与毛利的成本分摊。
 */
@Service
@RequiredArgsConstructor
public class TripFeeService {

    private static final String GLOBAL_RATIO_KEY = "fee.volume_weight_ratio";
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("333");

    /** 车次级成本项取数列白名单：source_field → biz_trip 列名 */
    private static final Map<String, String> TRIP_COLUMNS = Map.ofEntries(
            Map.entry("biz_trip.prepaid_transport_fee", "prepaid_transport_fee"),
            Map.entry("biz_trip.return_transport_fee", "return_transport_fee"),
            Map.entry("biz_trip.arrival_transport_fee", "arrival_transport_fee"),
            Map.entry("biz_trip.prepaid_oil_card_fee", "prepaid_oil_card_fee"),
            Map.entry("biz_trip.whole_weighing_fee", "whole_weighing_fee"),
            Map.entry("biz_trip.whole_insurance_fee", "whole_insurance_fee"),
            Map.entry("biz_trip.bulk_load_fee", "bulk_load_fee"),
            Map.entry("biz_trip.bulk_unload_fee", "bulk_unload_fee"),
            Map.entry("biz_trip.bulk_other_fee", "bulk_other_fee"),
            Map.entry("biz_trip.total_pickup_cost", "total_pickup_cost"),
            Map.entry("biz_trip.total_delivery_fee", "total_delivery_fee"),
            Map.entry("biz_trip.total_transfer_fee", "total_transfer_fee"));

    private final TripService tripService;
    private final BizTripMapper tripMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizTripFeeMapper tripFeeMapper;
    private final BizTripWaybillFeeMapper tripWaybillFeeMapper;
    private final BizWaybillRouteNodeMapper nodeMapper;
    private final SysRouteMapper routeMapper;
    private final SysFeeTypeMapper feeTypeMapper;
    private final SysConfigMapper configMapper;
    private final SysUserMapper userMapper;
    private final UserOrgService userOrgService;
    private final TripSummaryService summaryService;
    private final TripLogService tripLogService;

    /* ---------------- #12 预览 ---------------- */

    @DS("master")
    public FeePreviewVO preview(Long tripId, Integer allocMethod, List<TripFeeSaveRequest.Item> items) {
        BizTrip trip = tripService.requireVisible(tripId);
        int method = allocMethod == null ? validMethod(trip.getAllocMethod()) : validMethod(allocMethod);
        Map<String, BigDecimal> amounts = items == null || items.isEmpty()
                ? savedAmounts(tripId)
                : requestAmounts(items);
        return build(trip, method, amounts, rows(tripId)).vo();
    }

    /* ---------------- #11 保存 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public FeePreviewVO save(Long tripId, TripFeeSaveRequest request) {
        BizTrip trip = tripService.requireVisible(tripId);
        requireEditable(trip);
        int method = validMethod(request.getAllocMethod());
        Map<String, BigDecimal> submitted = requestAmounts(request.getItems());
        Map<String, SysFeeType> types = tripLevelTypes();

        // 未提交的历史项按 0 处理，保证费用行与分摊口径一致
        Map<String, BigDecimal> amounts = new LinkedHashMap<>(savedAmounts(tripId));
        amounts.replaceAll((code, value) -> BigDecimal.ZERO);
        amounts.putAll(submitted);
        for (String code : submitted.keySet()) {
            if (!types.containsKey(code)) {
                throw new BizException(400, "费用项不存在或未启用：" + code);
            }
        }

        UserOrgService.UserOrg org = userOrgService.current();
        String operatorName = operatorName(org.userId());
        Long operatorId = org.userId();
        Map<String, Object> before = feeSnapshot(trip, savedAmounts(tripId));

        for (Map.Entry<String, BigDecimal> entry : amounts.entrySet()) {
            SysFeeType type = types.get(entry.getKey());
            if (type == null) {
                continue;
            }
            tripFeeMapper.upsertTripLevel(tripId, type.getFeeCode(), type.getFeeName(), entry.getValue(),
                    type.getProfitEnabled(), type.getProfitDirection(), type.getSortOrder(),
                    trip.getDeptId(), operatorId);
        }

        UpdateWrapper<BizTrip> tripUpdate = new UpdateWrapper<>();
        tripUpdate.eq("id", tripId)
                .set("alloc_method", method)
                .set("update_by", operatorId)
                .set("update_by_name", operatorName);
        for (Map.Entry<String, BigDecimal> entry : amounts.entrySet()) {
            SysFeeType type = types.get(entry.getKey());
            String column = type == null ? null : TRIP_COLUMNS.get(type.getSourceField());
            if (column != null) {
                tripUpdate.set(column, entry.getValue());
            }
        }
        tripMapper.update(null, tripUpdate);

        List<BizTripWaybill> rows = rows(tripId);
        Built built = build(trip, method, amounts, rows);
        persistAllocation(trip, method, built, rows);
        summaryService.recalc(tripId);

        tripLogService.record(tripId, trip.getTripNo(), 12, null, trip.getDeptId(),
                operatorId, operatorName, before, feeSnapshot(trip, amounts), "费用信息变更");
        return built.vo();
    }

    /* ---------------- 分摊构建 ---------------- */

    private record Built(FeePreviewVO vo, List<BizTripWaybillFee> feeRows,
                         Map<Long, BigDecimal> allocatedByUnit, Map<Long, BigDecimal> profitByUnit) {
    }

    private Built build(BizTrip trip, int method, Map<String, BigDecimal> amounts, List<BizTripWaybill> rows) {
        Map<String, SysFeeType> types = tripLevelTypes();
        Map<Long, BigDecimal> ratioByWaybill = chargeRatios(rows);

        List<FeeAllocator.Unit> allocUnits = new ArrayList<>();
        Map<Long, BigDecimal> chargeWeights = new HashMap<>();
        Map<Long, BigDecimal> ratioByUnit = new HashMap<>();
        for (BizTripWaybill row : rows) {
            BigDecimal ratio = ratioByWaybill.getOrDefault(row.getWaybillId(), globalRatio());
            BigDecimal chargeWeight = chargeWeight(row, ratio);
            chargeWeights.put(row.getId(), chargeWeight);
            ratioByUnit.put(row.getId(), ratio);
            allocUnits.add(new FeeAllocator.Unit(row.getId(), nvl(row.getWeight()), nvl(row.getVolume()), chargeWeight));
        }

        Map<Long, Map<String, BigDecimal>> feesByUnit = new LinkedHashMap<>();
        Map<Long, BigDecimal> allocatedByUnit = new LinkedHashMap<>();
        Map<Long, BigDecimal> profitByUnit = new LinkedHashMap<>();
        rows.forEach(row -> {
            feesByUnit.put(row.getId(), new LinkedHashMap<>());
            allocatedByUnit.put(row.getId(), BigDecimal.ZERO);
            profitByUnit.put(row.getId(), BigDecimal.ZERO);
        });

        List<FeePreviewVO.ItemTotal> itemTotals = new ArrayList<>();
        List<BizTripWaybillFee> feeRows = new ArrayList<>();
        List<FeeAllocator.Result> firstResults = null;
        BigDecimal basisTotal = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : amounts.entrySet()) {
            SysFeeType type = types.get(entry.getKey());
            if (type == null) {
                continue;
            }
            FeePreviewVO.ItemTotal itemTotal = new FeePreviewVO.ItemTotal();
            itemTotal.setFeeCode(type.getFeeCode());
            itemTotal.setFeeName(type.getFeeName());
            itemTotal.setAmount(entry.getValue());
            itemTotal.setProfitEnabled(type.getProfitEnabled());
            itemTotals.add(itemTotal);

            List<FeeAllocator.Result> results = FeeAllocator.allocate(entry.getValue(), allocUnits, method);
            if (firstResults == null && !results.isEmpty()) {
                firstResults = results;
                basisTotal = results.get(0).basisTotal();
            }
            for (FeeAllocator.Result result : results) {
                feesByUnit.get(result.unitId()).put(type.getFeeCode(), result.amount());
                allocatedByUnit.merge(result.unitId(), result.amount(), BigDecimal::add);
                if (Boolean.TRUE.equals(type.getProfitEnabled())) {
                    profitByUnit.merge(result.unitId(), result.amount(), BigDecimal::add);
                }
                BizTripWaybillFee row = new BizTripWaybillFee();
                row.setTripId(trip.getId());
                row.setTripWaybillId(result.unitId());
                row.setFeeCode(type.getFeeCode());
                row.setFeeName(type.getFeeName());
                row.setAllocMethod(method);
                row.setBasisValue(result.basis());
                row.setBasisTotal(result.basisTotal());
                row.setAllocRatio(result.ratio());
                row.setAmount(result.amount());
                row.setDeptId(trip.getDeptId());
                feeRows.add(row);
            }
        }

        FeePreviewVO vo = new FeePreviewVO();
        vo.setTripId(trip.getId());
        vo.setAllocMethod(method);
        vo.setAllocMethodName(allocMethodName(method));
        vo.setBasisTotal(basisTotal);
        vo.setItems(itemTotals);

        List<FeePreviewVO.Unit> units = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal rebate = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        for (BizTripWaybill row : rows) {
            FeePreviewVO.Unit unit = new FeePreviewVO.Unit();
            unit.setTripWaybillId(row.getId());
            unit.setWaybillId(row.getWaybillId());
            unit.setWaybillNo(row.getWaybillNo());
            unit.setParentNo(row.getParentNo());
            unit.setIsChild(row.getIsChild());
            unit.setQuantity(row.getQuantity());
            unit.setWeight(nvl(row.getWeight()));
            unit.setVolume(nvl(row.getVolume()));
            unit.setChargeWeight(chargeWeights.get(row.getId()));
            unit.setVolumeWeightRatio(ratioByUnit.get(row.getId()));
            unit.setBasis(FeeAllocator.basis(new FeeAllocator.Unit(row.getId(), nvl(row.getWeight()),
                    nvl(row.getVolume()), chargeWeights.get(row.getId())), method));
            unit.setRatio(basisTotal.signum() == 0 ? BigDecimal.ZERO
                    : unit.getBasis().divide(basisTotal, 8, RoundingMode.HALF_UP));
            unit.setFees(feesByUnit.get(row.getId()));
            unit.setAllocatedFee(allocatedByUnit.get(row.getId()));
            unit.setProfitCost(profitByUnit.get(row.getId()));

            BigDecimal ticketIncome = nvl(row.getTotalFee());
            BigDecimal ticketRebate = nvl(row.getRebateTotal());
            BigDecimal ticketCost = profitByUnit.get(row.getId());
            income = income.add(ticketIncome);
            rebate = rebate.add(ticketRebate);
            cost = cost.add(ticketCost);
            gross = gross.add(ticketIncome.subtract(ticketRebate).subtract(ticketCost));
            units.add(unit);
        }
        vo.setUnits(units);

        FeePreviewVO.Summary summary = new FeePreviewVO.Summary();
        summary.setUnitCount(units.size());
        summary.setIncomeTotal(income);
        summary.setRebateTotal(rebate);
        summary.setCostTotal(cost);
        summary.setGrossProfit(gross);
        vo.setSummary(summary);
        return new Built(vo, feeRows, allocatedByUnit, profitByUnit);
    }

    private void persistAllocation(BizTrip trip, int method, Built built,
                                   List<BizTripWaybill> rows) {
        tripWaybillFeeMapper.delete(Wrappers.<BizTripWaybillFee>lambdaQuery()
                .eq(BizTripWaybillFee::getTripId, trip.getId()));
        Map<Long, Long> waybillByUnit = new HashMap<>();
        for (BizTripWaybill row : rows) {
            waybillByUnit.put(row.getId(), row.getWaybillId());
        }
        for (BizTripWaybillFee row : built.feeRows()) {
            row.setWaybillId(waybillByUnit.get(row.getTripWaybillId()));
            row.setDeptId(trip.getDeptId());
            tripWaybillFeeMapper.insert(row);
        }
        for (BizTripWaybill row : rows) {
            BigDecimal allocated = built.allocatedByUnit().getOrDefault(row.getId(), BigDecimal.ZERO);
            BigDecimal profitCost = built.profitByUnit().getOrDefault(row.getId(), BigDecimal.ZERO);
            BigDecimal gross = nvl(row.getTotalFee()).subtract(nvl(row.getRebateTotal())).subtract(profitCost);
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", row.getId())
                    .set("allocated_fee", allocated)
                    .set("profit_cost", profitCost)
                    .set("gross_profit", gross));
        }
    }

    /* ---------------- 配载变化后重算分摊 ---------------- */

    /**
     * 配载/移出/拆单/作废导致配载单元变化时，按已保存费用重算分摊：
     * 保证每项 Σ分摊 = 该项总额；无费用记录时不做任何事。
     */
    @Transactional(rollbackFor = Exception.class)
    @DS("master")
    public void reallocateIfConfigured(Long tripId) {
        BizTrip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            return;
        }
        Map<String, BigDecimal> amounts = savedAmounts(tripId);
        if (amounts.isEmpty()) {
            return;
        }
        int method = validMethod(trip.getAllocMethod());
        List<BizTripWaybill> rows = rows(tripId);
        Built built = build(trip, method, amounts, rows);
        persistAllocation(trip, method, built, rows);
        summaryService.recalc(tripId);
    }

    /* ---------------- 供发车校验复用 ---------------- */

    /** 发车前置：校验费用已保存且 D14 基数合计 > 0（存在正金额成本项时） */
    public void assertReadyForDepart(BizTrip trip, List<BizTripWaybill> rows) {
        List<BizTripFee> saved = tripFeeMapper.selectList(Wrappers.<BizTripFee>lambdaQuery()
                .eq(BizTripFee::getTripId, trip.getId())
                .eq(BizTripFee::getTripDestId, 0L)
                .eq(BizTripFee::getFeeStatus, 1));
        if (saved.isEmpty()) {
            throw new BizException(400, "请先保存费用信息后再发车");
        }
        int method = validMethod(trip.getAllocMethod());
        Map<Long, BigDecimal> ratioByWaybill = chargeRatios(rows);
        List<FeeAllocator.Unit> units = new ArrayList<>();
        for (BizTripWaybill row : rows) {
            BigDecimal ratio = ratioByWaybill.getOrDefault(row.getWaybillId(), globalRatio());
            units.add(new FeeAllocator.Unit(row.getId(), nvl(row.getWeight()), nvl(row.getVolume()),
                    chargeWeight(row, ratio)));
        }
        BigDecimal basisTotal = units.stream().map(unit -> FeeAllocator.basis(unit, method))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasPositive = saved.stream().anyMatch(fee -> nvl(fee.getAmount()).signum() > 0);
        if (hasPositive && basisTotal.signum() == 0) {
            throw new BizException(400, "分摊基数合计为 0，请补充运单重量/体积后重试");
        }
    }

    /* ---------------- 查询与工具 ---------------- */

    public List<BizTripWaybill> rows(Long tripId) {
        return tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripId, tripId)
                .orderByAsc(BizTripWaybill::getId));
    }

    private Map<String, BigDecimal> savedAmounts(Long tripId) {
        List<BizTripFee> saved = tripFeeMapper.selectList(Wrappers.<BizTripFee>lambdaQuery()
                .eq(BizTripFee::getTripId, tripId)
                .eq(BizTripFee::getTripDestId, 0L)
                .eq(BizTripFee::getFeeStatus, 1)
                .orderByAsc(BizTripFee::getSortOrder));
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (BizTripFee fee : saved) {
            amounts.put(fee.getFeeCode(), nvl(fee.getAmount()));
        }
        return amounts;
    }

    private Map<String, BigDecimal> requestAmounts(List<TripFeeSaveRequest.Item> items) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        if (items == null) {
            return amounts;
        }
        Map<String, SysFeeType> types = tripLevelTypes();
        for (TripFeeSaveRequest.Item item : items) {
            String code = item.getFeeCode() == null ? null : item.getFeeCode().trim();
            if (code == null || !types.containsKey(code)) {
                throw new BizException(400, "费用项不存在或未启用：" + item.getFeeCode());
            }
            if (item.getAmount() == null || item.getAmount().signum() < 0) {
                throw new BizException(400, "费用金额不能为负数：" + code);
            }
            amounts.put(code, item.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        return amounts;
    }

    /** 可录入的车次级成本项：启用、成本类、取数来源为 biz_trip.* */
    private Map<String, SysFeeType> tripLevelTypes() {
        List<SysFeeType> types = feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                .eq(SysFeeType::getEnabled, true)
                .eq(SysFeeType::getFeeCategory, 2)
                .likeRight(SysFeeType::getSourceField, "biz_trip.")
                .orderByAsc(SysFeeType::getSortOrder));
        Map<String, SysFeeType> map = new LinkedHashMap<>();
        for (SysFeeType type : types) {
            if (TRIP_COLUMNS.containsKey(type.getSourceField())) {
                map.put(type.getFeeCode(), type);
            }
        }
        return map;
    }

    private Map<Long, BigDecimal> chargeRatios(List<BizTripWaybill> rows) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (rows.isEmpty()) {
            return result;
        }
        List<Long> waybillIds = rows.stream().map(BizTripWaybill::getWaybillId).distinct().toList();
        List<BizWaybillRouteNode> nodes = nodeMapper.selectList(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .in(BizWaybillRouteNode::getWaybillId, waybillIds));
        Map<String, Long> routeByNode = new HashMap<>();
        Set<Long> routeIds = new LinkedHashSet<>();
        for (BizWaybillRouteNode node : nodes) {
            if (Objects.equals(node.getRouteScope(), 1) && node.getRouteId() != null) {
                routeByNode.put(node.getWaybillId() + ":" + node.getSeq(), node.getRouteId());
                routeIds.add(node.getRouteId());
            }
        }
        Map<Long, BigDecimal> routeRatios = new HashMap<>();
        if (!routeIds.isEmpty()) {
            for (SysRoute route : routeMapper.selectBatchIds(routeIds)) {
                routeRatios.put(route.getId(), route.getVolumeWeightRatio());
            }
        }
        BigDecimal global = globalRatio();
        for (BizTripWaybill row : rows) {
            Long routeId = routeByNode.get(row.getWaybillId() + ":" + row.getRouteNodeSeq());
            BigDecimal ratio = routeId == null ? null : routeRatios.get(routeId);
            result.put(row.getWaybillId(), ratio == null || ratio.signum() <= 0 ? global : ratio);
        }
        return result;
    }

    private BigDecimal globalRatio() {
        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, GLOBAL_RATIO_KEY)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return DEFAULT_RATIO;
        }
        try {
            BigDecimal value = new BigDecimal(config.getConfigValue().trim());
            return value.signum() <= 0 ? DEFAULT_RATIO : value;
        } catch (NumberFormatException ex) {
            return DEFAULT_RATIO;
        }
    }

    private BigDecimal chargeWeight(BizTripWaybill row, BigDecimal ratio) {
        BigDecimal byVolume = nvl(row.getVolume()).multiply(nvl(ratio)).setScale(3, RoundingMode.HALF_UP);
        return nvl(row.getWeight()).max(byVolume);
    }

    private void requireEditable(BizTrip trip) {
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException(400, "发车后费用已锁定，不能修改");
        }
    }

    private int validMethod(Integer method) {
        if (method == null || method < TripEnums.AllocMethod.WEIGHT
                || method > TripEnums.AllocMethod.CHARGE_WEIGHT) {
            throw new BizException(400, "分摊方式不合法");
        }
        return method;
    }

    private String allocMethodName(int method) {
        return switch (method) {
            case TripEnums.AllocMethod.WEIGHT -> "按重量";
            case TripEnums.AllocMethod.VOLUME -> "按体积";
            case TripEnums.AllocMethod.CHARGE_WEIGHT -> "按重泡比";
            default -> "未知";
        };
    }

    private Map<String, Object> feeSnapshot(BizTrip trip, Map<String, BigDecimal> amounts) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allocMethod", trip.getAllocMethod());
        map.put("items", amounts);
        return map;
    }

    private String operatorName(Long userId) {
        if (userId == null) {
            return "system";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getRealName()) ? "system" : user.getRealName();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
