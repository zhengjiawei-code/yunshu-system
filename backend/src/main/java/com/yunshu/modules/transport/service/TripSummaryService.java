package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 车次汇总重算：车次毛利 = Σ已配载票毛利；收入/返款/成本直接汇总配载快照。
 * 所有配载、移出、拆单、改单、作废动作完成后统一调用，禁止各业务点各算一套。
 */
@Service
@RequiredArgsConstructor
@DS("master")
public class TripSummaryService {

    private final BizTripMapper tripMapper;
    private final BizTripDestMapper destMapper;
    private final BizTripWaybillMapper tripWaybillMapper;

    public void recalc(Long tripId) {
        List<BizTripWaybill> rows = tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripId, tripId));
        BizTrip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            return;
        }

        int count = rows.size();
        int quantity = rows.stream().mapToInt(r -> nvl(r.getQuantity())).sum();
        BigDecimal weight = rows.stream().map(r -> nvl(r.getWeight())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal volume = rows.stream().map(r -> nvl(r.getVolume())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prepaid = sum(rows, BizTripWaybill::getPrepaidFee);
        BigDecimal collect = sum(rows, BizTripWaybill::getCollectFee);
        BigDecimal returnFee = sum(rows, BizTripWaybill::getReturnFee);
        BigDecimal monthly = sum(rows, BizTripWaybill::getMonthlyFee);
        BigDecimal totalFreight = sum(rows, BizTripWaybill::getTotalFee);
        BigDecimal rebate = sum(rows, BizTripWaybill::getRebateTotal);
        BigDecimal validFreight = sum(rows, BizTripWaybill::getValidFreight);
        BigDecimal allocated = sum(rows, BizTripWaybill::getAllocatedFee);
        BigDecimal profitCost = sum(rows, BizTripWaybill::getProfitCost);
        BigDecimal gross = totalFreight.subtract(rebate).subtract(profitCost);

        UpdateWrapper<BizTrip> update = new UpdateWrapper<>();
        update.eq("id", tripId)
                .set("total_waybill_count", count)
                .set("total_quantity", quantity)
                .set("total_weight", weight)
                .set("total_volume", volume)
                .set("total_prepaid_freight", prepaid)
                .set("total_collect_freight", collect)
                .set("total_return_freight", returnFee)
                .set("total_monthly_freight", monthly)
                .set("total_freight", totalFreight)
                .set("total_rebate", rebate)
                .set("total_valid_freight", validFreight)
                .set("total_cost_fee", allocated)
                .set("gross_profit", gross)
                .set("load_rate_weight", rate(weight, trip.getVehicleLoad()))
                .set("load_rate_volume", rate(volume, trip.getVehicleVolume()));
        tripMapper.update(null, update);

        Map<Long, List<BizTripWaybill>> byDest = new HashMap<>();
        for (BizTripWaybill row : rows) {
            byDest.computeIfAbsent(row.getTripDestId(), key -> new java.util.ArrayList<>()).add(row);
        }
        for (BizTripDest dest : destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId))) {
            List<BizTripWaybill> destRows = byDest.getOrDefault(dest.getId(), List.of());
            UpdateWrapper<BizTripDest> destUpdate = new UpdateWrapper<>();
            destUpdate.eq("id", dest.getId())
                    .set("total_waybill_count", destRows.size())
                    .set("total_quantity", destRows.stream().mapToInt(r -> nvl(r.getQuantity())).sum())
                    .set("total_weight", destRows.stream().map(r -> nvl(r.getWeight()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .set("total_volume", destRows.stream().map(r -> nvl(r.getVolume()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
            destMapper.update(null, destUpdate);
        }
    }

    private BigDecimal sum(List<BizTripWaybill> rows, java.util.function.Function<BizTripWaybill, BigDecimal> getter) {
        return rows.stream().map(getter).map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal rate(BigDecimal value, BigDecimal capacity) {
        if (value == null || capacity == null || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(capacity, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
