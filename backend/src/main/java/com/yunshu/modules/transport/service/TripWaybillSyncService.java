package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 运单改单/作废与配载的联动：同一事务刷新配载快照与车次汇总。
 * 改单只允许改收货信息等非数量金额字段；作废整票时若存在已装车子单则拒绝。
 */
@Service
@RequiredArgsConstructor
@DS("master")
public class TripWaybillSyncService {

    private static final Set<String> LOCKED_FIELDS = Set.of(
            "quantity", "weight", "volume",
            "freightFee", "deliveryFee", "pickupFee", "otherFee", "receiptFee", "makeBillFee",
            "insuranceFee", "cashBack", "oweBack", "codAmount",
            "prepaidFee", "collectFee", "monthlyFee", "returnFee",
            "payMethod", "deliveryMethod", "goodsList");

    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizTripMapper tripMapper;
    private final BizWaybillMapper waybillMapper;
    private final TripSummaryService summaryService;
    private final TripFeeService tripFeeService;
    private final TripLogService tripLogService;
    private final UserOrgService userOrgService;

    /** 改单前置校验：已拆或已配载的单元禁止改件数/重量/体积/金额 */
    public void assertAmendAllowed(BizWaybill waybill, Set<String> fields) {
        if (fields == null || fields.stream().noneMatch(LOCKED_FIELDS::contains)) {
            return;
        }
        boolean locked = Objects.equals(waybill.getIsChild(), 1)
                || childCount(waybill.getId()) > 0
                || activeCount(waybill.getId()) > 0;
        if (locked) {
            throw new BizException(400, "已拆单或已配载的运单禁止修改件数、重量、体积与金额");
        }
    }

    /** 作废前置校验：母单存在已装车子单时禁止整票作废 */
    public void assertVoidAllowed(BizWaybill waybill) {
        if (!Objects.equals(waybill.getIsChild(), 1) && childCount(waybill.getId()) > 0) {
            List<BizWaybill> children = waybillMapper.selectList(Wrappers.<BizWaybill>lambdaQuery()
                    .eq(BizWaybill::getParentId, waybill.getId())
                    .eq(BizWaybill::getIsChild, 1)
                    .ne(BizWaybill::getStatus, 900));
            for (BizWaybill child : children) {
                if (activeCount(child.getId()) > 0) {
                    throw new BizException(400, "存在已装车子单，禁止整票作废，请先移出或作废子单");
                }
            }
        }
    }

    /** 改单后刷新配载快照与车次汇总 */
    public void onAmend(BizWaybill after, Set<String> fields) {
        List<BizTripWaybill> rows = activeRows(after.getId());
        if (rows.isEmpty()) {
            return;
        }
        for (BizTripWaybill tw : rows) {
            UpdateWrapper<BizTripWaybill> update = new UpdateWrapper<>();
            update.eq("id", tw.getId())
                    .set("quantity", after.getQuantity())
                    .set("weight", nvl(after.getWeight()))
                    .set("volume", nvl(after.getVolume()))
                    .set("prepaid_fee", nvl(after.getPrepaidFee()))
                    .set("collect_fee", nvl(after.getCollectFee()))
                    .set("return_fee", nvl(after.getReturnFee()))
                    .set("monthly_fee", nvl(after.getMonthlyFee()))
                    .set("total_fee", nvl(after.getTotalFee()))
                    .set("rebate_total", nvl(after.getRebateTotal()))
                    .set("valid_freight", nvl(after.getValidFreight()))
                    .set("customer_name", after.getCustomerName())
                    .set("receiver_name", after.getReceiverName())
                    .set("receiver_phone", after.getReceiverPhone())
                    .set("dest_name", after.getDestName())
                    .set("goods_name", after.getGoodsName());
            tripWaybillMapper.update(null, update);
        }
        refreshTrips(rows, 13, "运单改单刷新");
        // 快照变化后重算分摊与票毛利（票毛利按当前运单毛利公式），保证车次毛利=Σ票毛利
        rows.stream().map(BizTripWaybill::getTripId).distinct()
                .forEach(tripFeeService::reallocateIfConfigured);
    }

    /** 作废后解除配载、恢复运单待配载标记并重算车次 */
    public void onVoid(BizWaybill after) {
        List<BizTripWaybill> rows = activeRows(after.getId());
        waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                .eq("id", after.getId())
                .set("load_status", TripEnums.LoadStatus.PENDING));
        if (rows.isEmpty()) {
            return;
        }
        for (BizTripWaybill tw : rows) {
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", tw.getId())
                    .set("deleted", 1)
                    .set("update_by", userOrgService.current().userId()));
        }
        refreshTrips(rows, 14, "运单作废解除配载");
        rows.stream().map(BizTripWaybill::getTripId).distinct()
                .forEach(tripFeeService::reallocateIfConfigured);
    }

    private void refreshTrips(List<BizTripWaybill> rows, int action, String reason) {
        Long userId = userOrgService.current().userId();
        rows.stream().map(BizTripWaybill::getTripId).distinct().forEach(tripId -> {
            summaryService.recalc(tripId);
            BizTrip trip = tripMapper.selectById(tripId);
            if (trip == null) {
                return;
            }
            long remain = tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                    .eq(BizTripWaybill::getTripId, tripId));
            if (remain == 0 && Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
                tripMapper.update(null, new UpdateWrapper<BizTrip>().eq("id", tripId)
                        .set("trip_status", TripEnums.TripStatus.PLAN));
            }
            tripLogService.record(tripId, trip.getTripNo(), action, null, trip.getDeptId(),
                    userId, "system", null, null, reason);
        });
    }

    private List<BizTripWaybill> activeRows(Long waybillId) {
        return tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getWaybillId, waybillId)
                .in(BizTripWaybill::getLoadStatus, 1, 2, 3));
    }

    private long activeCount(Long waybillId) {
        return tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getWaybillId, waybillId)
                .in(BizTripWaybill::getLoadStatus, 1, 2, 3));
    }

    private long childCount(Long parentId) {
        return waybillMapper.selectCount(Wrappers.<BizWaybill>lambdaQuery()
                .eq(BizWaybill::getParentId, parentId)
                .eq(BizWaybill::getIsChild, 1)
                .ne(BizWaybill::getStatus, 900));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
