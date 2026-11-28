package com.yunshu.modules.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.entity.BizWaybillLog;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import com.yunshu.modules.operation.mapper.BizWaybillLogMapper;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.operation.mapper.BizWaybillRouteNodeMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.SplitRequest;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import com.yunshu.modules.transport.vo.SplitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 拆单装车：未配载整票按件数拆出子单并直接装本车，母单余额回池。
 * 金额按件数比例拆分、尾差归母单；重量/体积按件数占比带过，母子合计与拆前一分不差。
 */
@Service
@RequiredArgsConstructor
public class TripSplitService {

    private static final int VOIDED = 900;

    private final TripService tripService;
    private final BizTripMapper tripMapper;
    private final BizTripDestMapper destMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizWaybillMapper waybillMapper;
    private final BizWaybillRouteNodeMapper nodeMapper;
    private final BizWaybillLogMapper waybillLogMapper;
    private final TripSummaryService summaryService;
    private final TripFeeService tripFeeService;
    private final GrossFormulaService grossFormulaService;
    private final TripLogService tripLogService;
    private final UserOrgService userOrgService;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @DS("slave")
    public SplitVO preview(Long tripId, Long parentWaybillId, Integer quantity) {
        BizTrip trip = tripService.requireVisible(tripId);
        requireLoadingStatus(trip);
        BizWaybill parent = waybillMapper.selectById(parentWaybillId);
        ChildAgg agg = validateAndAggregate(trip, parent, quantity);
        SplitVO vo = buildPreview(parent, quantity, agg);
        vo.setChildSeq(nextChildSeq(parent.getId()));
        NextDest next = resolveNext(trip, parent);
        vo.setChildStatus(70);
        vo.setRouteNodeCount(next == null ? 0 : 1);
        return vo;
    }

    /**
     * READ COMMITTED：母单行锁等待期间，默认 REPEATABLE READ 的快照已在 requireVisible 时定格，
     * 等锁后读到的子单仍是旧值，并发拆单合并会互相覆盖导致丢量。
     * 读已提交保证拿到母单锁之后的读取都是最新已提交数据。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @DS("master")
    public SplitVO splitLoad(Long tripId, SplitRequest request) {
        BizTrip trip = tripService.requireVisible(tripId);
        requireLoadingStatus(trip);
        BizWaybill parent = waybillMapper.selectByIdForUpdate(request.getParentWaybillId());
        ChildAgg agg = validateAndAggregate(trip, parent, request.getQuantity());
        int q = request.getQuantity();
        NextDest next = resolveNext(trip, parent);
        if (next == null) {
            throw new BizException(409, "运单下一节点已变化，请刷新");
        }
        long active = tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getWaybillId, parent.getId())
                .in(BizTripWaybill::getLoadStatus, 1, 2, 3));
        if (active > 0) {
            throw new BizException(409, "母单已被其他车次配载");
        }
        int childSeq = nextChildSeq(parent.getId());

        SplitVO vo = buildPreview(parent, q, agg);
        vo.setChildSeq(childSeq);
        BizWaybill child = buildChild(parent, vo.getChild(), childSeq, q);
        waybillMapper.insert(child);
        copyRouteNodes(parent.getId(), child.getId());
        BizTripWaybill tw = buildTripWaybill(child, trip, next.dest, next.node, q,
                parent.getQuantity(), parent.getStatus());
        tripWaybillMapper.insert(tw);

        // 同一子车次下同母单的多个子单自动合并，保留最早子单号
        MergeResult merged = mergeSameDestChildren(trip, parent, next.dest, child, tw);
        BizWaybill resultChild = merged.waybill;
        BizTripWaybill resultTw = merged.tripWaybill;

        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)) {
            tripMapper.update(null, new UpdateWrapper<BizTrip>().eq("id", tripId)
                    .set("trip_status", TripEnums.TripStatus.LOADING));
        }
        summaryService.recalc(tripId);
        tripFeeService.reallocateIfConfigured(tripId);

        int remaining = Math.max(agg.remainingQuantity - q, 0);
        writeWaybillLog(parent, resultChild, q, remaining);
        tripLogService.record(tripId, trip.getTripNo(), 3, null, trip.getDeptId(),
                userOrgService.current().userId(), operatorName(), null,
                Map.of("parentNo", parent.getWaybillNo(), "childNo", resultChild.getWaybillNo(),
                        "splitQuantity", q, "remainingQuantity", remaining), "拆单装车");

        vo.setChildWaybillId(resultChild.getId());
        vo.setChildWaybillNo(resultChild.getWaybillNo());
        vo.setChildSeq(resultChild.getChildSeq());
        vo.setChildStatus(resultChild.getStatus());
        vo.setRouteNodeCount(countRouteNodes(resultChild.getId()));
        return vo;
    }

    /**
     * 同一母单在同一子车次出现多条子单时合并：件数/重量/体积/金额相加，
     * 保留最早（childSeq 最小）子单，其余逻辑删除并写操作日志。
     */
    private MergeResult mergeSameDestChildren(BizTrip trip, BizWaybill parent, BizTripDest dest,
                                              BizWaybill child, BizTripWaybill childTw) {
        List<BizWaybill> siblings = waybillMapper.selectList(Wrappers.<BizWaybill>lambdaQuery()
                .eq(BizWaybill::getParentId, parent.getId())
                .eq(BizWaybill::getIsChild, 1)
                .ne(BizWaybill::getStatus, VOIDED)
                .in(BizWaybill::getLoadStatus, 1, 2, 3));
        if (siblings.size() <= 1) {
            return new MergeResult(child, childTw);
        }
        Map<Long, BizWaybill> byId = new HashMap<>();
        siblings.forEach(item -> byId.put(item.getId(), item));
        List<BizTripWaybill> sameDest = tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripId, trip.getId())
                .eq(BizTripWaybill::getTripDestId, dest.getId())
                .in(BizTripWaybill::getWaybillId, byId.keySet())
                .in(BizTripWaybill::getLoadStatus, 1, 2, 3));
        if (sameDest.size() <= 1) {
            return new MergeResult(child, childTw);
        }
        BizTripWaybill keeperTw = sameDest.stream()
                .min(Comparator.comparing(item -> byId.get(item.getWaybillId()).getChildSeq(),
                        Comparator.nullsLast(Integer::compareTo)))
                .orElse(childTw);
        BizWaybill keeper = byId.getOrDefault(keeperTw.getWaybillId(), child);
        List<BizTripWaybill> others = sameDest.stream()
                .filter(item -> !Objects.equals(item.getId(), keeperTw.getId()))
                .toList();
        List<String> mergedNos = new ArrayList<>();
        for (BizTripWaybill other : others) {
            BizWaybill otherWb = byId.get(other.getWaybillId());
            if (otherWb == null) {
                continue;
            }
            mergedNos.add(otherWb.getWaybillNo());
            keeper.setQuantity(nvl(keeper.getQuantity()) + nvl(otherWb.getQuantity()));
            keeper.setWeight(nvl(keeper.getWeight()).add(nvl(otherWb.getWeight())));
            keeper.setVolume(nvl(keeper.getVolume()).add(nvl(otherWb.getVolume())));
            keeper.setDeliveryFee(nvl(keeper.getDeliveryFee()).add(nvl(otherWb.getDeliveryFee())));
            keeper.setPrepaidFee(nvl(keeper.getPrepaidFee()).add(nvl(otherWb.getPrepaidFee())));
            keeper.setCollectFee(nvl(keeper.getCollectFee()).add(nvl(otherWb.getCollectFee())));
            keeper.setReturnFee(nvl(keeper.getReturnFee()).add(nvl(otherWb.getReturnFee())));
            keeper.setMonthlyFee(nvl(keeper.getMonthlyFee()).add(nvl(otherWb.getMonthlyFee())));
            keeper.setTotalFee(nvl(keeper.getTotalFee()).add(nvl(otherWb.getTotalFee())));
            keeper.setFreightFee(nvl(keeper.getFreightFee()).add(nvl(otherWb.getFreightFee())));
            keeper.setCashBack(nvl(keeper.getCashBack()).add(nvl(otherWb.getCashBack())));
            keeper.setOweBack(nvl(keeper.getOweBack()).add(nvl(otherWb.getOweBack())));
            keeper.setRebateTotal(nvl(keeper.getRebateTotal()).add(nvl(otherWb.getRebateTotal())));
            keeper.setValidFreight(nvl(keeper.getValidFreight()).add(nvl(otherWb.getValidFreight())));

            waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", otherWb.getId())
                    .set("status", VOIDED)
                    .set("load_status", TripEnums.LoadStatus.PENDING)
                    // 释放子单号占位，运单号后缀保留不重写
                    .set("child_seq", null)
                    .set("update_by", userOrgService.current().userId()));
            tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                    .eq("id", other.getId())
                    .set("deleted", 1)
                    .set("update_by", userOrgService.current().userId()));
        }
        if (mergedNos.isEmpty()) {
            return new MergeResult(child, childTw);
        }
        waybillMapper.updateById(keeper);
        keeperTw.setQuantity(keeper.getQuantity());
        keeperTw.setWeight(nvl(keeper.getWeight()));
        keeperTw.setVolume(nvl(keeper.getVolume()));
        keeperTw.setPrepaidFee(nvl(keeper.getPrepaidFee()));
        keeperTw.setCollectFee(nvl(keeper.getCollectFee()));
        keeperTw.setReturnFee(nvl(keeper.getReturnFee()));
        keeperTw.setMonthlyFee(nvl(keeper.getMonthlyFee()));
        keeperTw.setTotalFee(nvl(keeper.getTotalFee()));
        keeperTw.setRebateTotal(nvl(keeper.getRebateTotal()));
        keeperTw.setValidFreight(nvl(keeper.getValidFreight()));
        Integer parentQty = parent.getQuantity();
        keeperTw.setSplitRatio(parentQty == null || parentQty <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(nvl(keeper.getQuantity()))
                        .divide(BigDecimal.valueOf(parentQty), 6, RoundingMode.HALF_UP));
        keeperTw.setGrossProfit(grossFormulaService.computeGross(
                keeperTw, Map.of(), grossFormulaService.current()));
        tripWaybillMapper.updateById(keeperTw);

        tripLogService.record(trip.getId(), trip.getTripNo(), 3, null, trip.getDeptId(),
                userOrgService.current().userId(), operatorName(), null,
                Map.of("parentNo", parent.getWaybillNo(), "keepChildNo", keeper.getWaybillNo(),
                        "mergedChildNos", String.join(",", mergedNos),
                        "mergedQuantity", nvl(keeper.getQuantity())), "同一子车次子单自动合并");
        return new MergeResult(keeper, keeperTw);
    }

    /* ---------------- 校验与计算 ---------------- */

    private ChildAgg validateAndAggregate(BizTrip trip, BizWaybill parent, Integer quantity) {
        if (parent == null) {
            throw new BizException("母单不存在或已删除");
        }
        if (Objects.equals(parent.getIsChild(), 1)) {
            throw new BizException("子单不能再次拆单");
        }
        if (!Objects.equals(parent.getLoadStatus(), TripEnums.LoadStatus.PENDING)) {
            throw new BizException("母单已配载，不能拆单");
        }
        if (!Objects.equals(parent.getStatus(), 20) && !Objects.equals(parent.getStatus(), 60)) {
            throw new BizException("母单当前状态不可拆单");
        }
        if (!Objects.equals(parent.getCurrentHubId(), trip.getOriginDeptId())) {
            throw new BizException("母单库存网点不在本车始发网点");
        }
        ChildAgg agg = aggregate(parent);
        if (agg.remainingQuantity <= 0) {
            throw new BizException("母单余额为0，无需配载");
        }
        if (quantity == null || quantity < 1) {
            throw new BizException("本车装载件数至少1件");
        }
        if (quantity > agg.remainingQuantity) {
            throw new BizException("本车装载件数不能超过剩余件数 " + agg.remainingQuantity);
        }
        return agg;
    }

    private ChildAgg aggregate(BizWaybill parent) {
        List<BizWaybill> children = waybillMapper.selectList(Wrappers.<BizWaybill>lambdaQuery()
                .eq(BizWaybill::getParentId, parent.getId())
                .eq(BizWaybill::getIsChild, 1)
                .ne(BizWaybill::getStatus, VOIDED)
                // 已撤销回池的子单不再占用余额
                .ne(BizWaybill::getLoadStatus, TripEnums.LoadStatus.PENDING));
        ChildAgg agg = new ChildAgg();
        agg.totalQuantity = nvl(parent.getQuantity());
        agg.remainingQuantity = agg.totalQuantity;
        agg.remainingWeight = nvl(parent.getWeight());
        agg.remainingVolume = nvl(parent.getVolume());
        agg.remainingDelivery = nvl(parent.getDeliveryFee());
        agg.remainingPrepaid = nvl(parent.getPrepaidFee());
        agg.remainingCollect = nvl(parent.getCollectFee());
        agg.remainingReturn = nvl(parent.getReturnFee());
        agg.remainingMonthly = nvl(parent.getMonthlyFee());
        agg.remainingCash = nvl(parent.getCashBack());
        agg.remainingOwe = nvl(parent.getOweBack());
        for (BizWaybill child : children) {
            agg.childCount++;
            agg.remainingQuantity -= nvl(child.getQuantity());
            agg.remainingWeight = agg.remainingWeight.subtract(nvl(child.getWeight()));
            agg.remainingVolume = agg.remainingVolume.subtract(nvl(child.getVolume()));
            agg.remainingDelivery = agg.remainingDelivery.subtract(nvl(child.getDeliveryFee()));
            agg.remainingPrepaid = agg.remainingPrepaid.subtract(nvl(child.getPrepaidFee()));
            agg.remainingCollect = agg.remainingCollect.subtract(nvl(child.getCollectFee()));
            agg.remainingReturn = agg.remainingReturn.subtract(nvl(child.getReturnFee()));
            agg.remainingMonthly = agg.remainingMonthly.subtract(nvl(child.getMonthlyFee()));
            agg.remainingCash = agg.remainingCash.subtract(nvl(child.getCashBack()));
            agg.remainingOwe = agg.remainingOwe.subtract(nvl(child.getOweBack()));
        }
        agg.remainingWeight = agg.remainingWeight.max(BigDecimal.ZERO);
        agg.remainingVolume = agg.remainingVolume.max(BigDecimal.ZERO);
        agg.remainingDelivery = agg.remainingDelivery.max(BigDecimal.ZERO);
        agg.remainingPrepaid = agg.remainingPrepaid.max(BigDecimal.ZERO);
        agg.remainingCollect = agg.remainingCollect.max(BigDecimal.ZERO);
        agg.remainingReturn = agg.remainingReturn.max(BigDecimal.ZERO);
        agg.remainingMonthly = agg.remainingMonthly.max(BigDecimal.ZERO);
        agg.remainingCash = agg.remainingCash.max(BigDecimal.ZERO);
        agg.remainingOwe = agg.remainingOwe.max(BigDecimal.ZERO);
        return agg;
    }

    private SplitVO buildPreview(BizWaybill parent, int q, ChildAgg agg) {
        SplitVO vo = new SplitVO();
        vo.setParentWaybillId(parent.getId());
        vo.setParentWaybillNo(parent.getWaybillNo());
        vo.setOriginalQuantity(parent.getQuantity());
        vo.setOriginalWeight(nvl(parent.getWeight()));
        vo.setOriginalVolume(nvl(parent.getVolume()));
        vo.setRemainingQuantityBefore(agg.remainingQuantity);
        vo.setRemainingWeightBefore(agg.remainingWeight);
        vo.setRemainingVolumeBefore(agg.remainingVolume);
        vo.setSplitQuantity(q);
        vo.setSplitRatio(BigDecimal.valueOf(q)
                .divide(BigDecimal.valueOf(agg.totalQuantity), 6, RoundingMode.HALF_UP));

        SplitVO.Amounts child = new SplitVO.Amounts();
        child.setQuantity(q);
        child.setWeight(splitWeight(agg.remainingWeight, q, agg.remainingQuantity));
        child.setVolume(splitWeight(agg.remainingVolume, q, agg.remainingQuantity));
        child.setDeliveryFee(split(agg.remainingDelivery, q, agg.remainingQuantity));
        child.setPrepaidFee(split(agg.remainingPrepaid, q, agg.remainingQuantity));
        child.setCollectFee(split(agg.remainingCollect, q, agg.remainingQuantity));
        child.setReturnFee(split(agg.remainingReturn, q, agg.remainingQuantity));
        child.setMonthlyFee(split(agg.remainingMonthly, q, agg.remainingQuantity));
        child.setCashBack(split(agg.remainingCash, q, agg.remainingQuantity));
        child.setOweBack(split(agg.remainingOwe, q, agg.remainingQuantity));
        fillTotals(child);

        SplitVO.Amounts remain = new SplitVO.Amounts();
        remain.setQuantity(agg.remainingQuantity - q);
        remain.setWeight(agg.remainingWeight.subtract(child.getWeight()));
        remain.setVolume(agg.remainingVolume.subtract(child.getVolume()));
        remain.setDeliveryFee(agg.remainingDelivery.subtract(child.getDeliveryFee()));
        remain.setPrepaidFee(agg.remainingPrepaid.subtract(child.getPrepaidFee()));
        remain.setCollectFee(agg.remainingCollect.subtract(child.getCollectFee()));
        remain.setReturnFee(agg.remainingReturn.subtract(child.getReturnFee()));
        remain.setMonthlyFee(agg.remainingMonthly.subtract(child.getMonthlyFee()));
        remain.setCashBack(agg.remainingCash.subtract(child.getCashBack()));
        remain.setOweBack(agg.remainingOwe.subtract(child.getOweBack()));
        fillTotals(remain);

        vo.setChild(child);
        vo.setParentRemaining(remain);
        return vo;
    }

    private void fillTotals(SplitVO.Amounts a) {
        BigDecimal total = nvl(a.getPrepaidFee()).add(nvl(a.getCollectFee()))
                .add(nvl(a.getReturnFee())).add(nvl(a.getMonthlyFee()));
        BigDecimal rebate = nvl(a.getCashBack()).add(nvl(a.getOweBack()));
        a.setTotalFee(total);
        a.setRebateTotal(rebate);
        a.setValidFreight(total.subtract(rebate));
    }

    /** 尾差归母单：本车件数=剩余件数时子单拿全部，否则子单四舍五入、母单做差 */
    private BigDecimal split(BigDecimal base, int quantity, int remaining) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (quantity >= remaining) {
            return base.setScale(2, RoundingMode.HALF_UP);
        }
        return base.multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(remaining), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal splitWeight(BigDecimal base, int quantity, int remaining) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (quantity >= remaining) {
            return base.setScale(3, RoundingMode.HALF_UP);
        }
        return base.multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(remaining), 3, RoundingMode.HALF_UP);
    }

    /* ---------------- 落库 ---------------- */

    /**
     * 子单号取号：当前有效子单（未作废、未逻辑删除）中最小未占用序号。
     * 调用方已对母单行 FOR UPDATE，同一母单串行取号，避免并发重号；
     * 作废子单仅释放内部占位（child_seq 置空），运单号后缀保留不重写。
     */
    private int nextChildSeq(Long parentId) {
        Set<Integer> used = waybillMapper.selectList(Wrappers.<BizWaybill>lambdaQuery()
                        .select(BizWaybill::getChildSeq)
                        .eq(BizWaybill::getParentId, parentId)
                        .eq(BizWaybill::getIsChild, 1)
                        .ne(BizWaybill::getStatus, VOIDED))
                .stream().map(BizWaybill::getChildSeq).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (int seq = 1; seq <= 99; seq++) {
            if (!used.contains(seq)) {
                return seq;
            }
        }
        throw new BizException("子单序号已达上限99，不能再拆");
    }

    private BizWaybill buildChild(BizWaybill parent, SplitVO.Amounts a, int childSeq, int q) {
        BizWaybill child = new BizWaybill();
        BeanUtils.copyProperties(parent, child);
        child.setId(null);
        child.setWaybillNo(parent.getWaybillNo() + "-" + String.format("%02d", childSeq));
        child.setParentId(parent.getId());
        child.setParentNo(parent.getWaybillNo());
        child.setIsChild(1);
        child.setChildSeq(childSeq);
        child.setPreOrderNo(null);
        child.setStatus(70);
        child.setLoadStatus(TripEnums.LoadStatus.LOADED);
        child.setQuantity(q);
        child.setWeight(a.getWeight());
        child.setVolume(a.getVolume());
        child.setFreightFee(a.getTotalFee());
        child.setDeliveryFee(a.getDeliveryFee());
        child.setPrepaidFee(a.getPrepaidFee());
        child.setCollectFee(a.getCollectFee());
        child.setReturnFee(a.getReturnFee());
        child.setMonthlyFee(a.getMonthlyFee());
        child.setTotalFee(a.getTotalFee());
        child.setCashBack(a.getCashBack());
        child.setOweBack(a.getOweBack());
        child.setRebateTotal(a.getRebateTotal());
        child.setValidFreight(a.getValidFreight());
        child.setPickupFee(BigDecimal.ZERO);
        child.setOtherFee(BigDecimal.ZERO);
        child.setReceiptFee(BigDecimal.ZERO);
        child.setMakeBillFee(BigDecimal.ZERO);
        child.setInsuranceFee(BigDecimal.ZERO);
        child.setCodAmount(BigDecimal.ZERO);
        child.setCreateBy(userOrgService.current().userId());
        child.setCreateByName(operatorName());
        child.setCreateTime(null);
        child.setUpdateBy(userOrgService.current().userId());
        child.setUpdateTime(null);
        child.setDeleted(0);
        return child;
    }

    private void copyRouteNodes(Long parentId, Long childId) {
        List<BizWaybillRouteNode> nodes = nodeMapper.selectList(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .eq(BizWaybillRouteNode::getWaybillId, parentId)
                .orderByAsc(BizWaybillRouteNode::getSeq));
        for (BizWaybillRouteNode node : nodes) {
            BizWaybillRouteNode copy = new BizWaybillRouteNode();
            BeanUtils.copyProperties(node, copy);
            copy.setId(null);
            copy.setWaybillId(childId);
            nodeMapper.insert(copy);
        }
    }

    private BizTripWaybill buildTripWaybill(BizWaybill child, BizTrip trip, BizTripDest dest,
                                            BizWaybillRouteNode node, int q, Integer parentQuantity,
                                            Integer statusBeforeLoad) {
        BizTripWaybill tw = new BizTripWaybill();
        tw.setTripId(trip.getId());
        tw.setTripDestId(dest.getId());
        tw.setSubTripNo(dest.getSubTripNo());
        tw.setWaybillId(child.getId());
        tw.setWaybillNo(child.getWaybillNo());
        tw.setParentNo(child.getParentNo());
        tw.setIsChild(1);
        tw.setSplitRatio(parentQuantity == null || parentQuantity <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(q).divide(BigDecimal.valueOf(parentQuantity), 6, RoundingMode.HALF_UP));
        tw.setRouteNodeSeq(node.getSeq());
        tw.setDestHubId(node.getToDeptId());
        tw.setDestHubName(node.getToDeptName());
        tw.setDestName(child.getDestName());
        tw.setCustomerName(child.getCustomerName());
        tw.setReceiverName(child.getReceiverName());
        tw.setReceiverPhone(child.getReceiverPhone());
        tw.setGoodsName(child.getGoodsName());
        tw.setQuantity(child.getQuantity());
        tw.setWeight(nvl(child.getWeight()));
        tw.setVolume(nvl(child.getVolume()));
        tw.setPrepaidFee(nvl(child.getPrepaidFee()));
        tw.setCollectFee(nvl(child.getCollectFee()));
        tw.setReturnFee(nvl(child.getReturnFee()));
        tw.setMonthlyFee(nvl(child.getMonthlyFee()));
        tw.setTotalFee(nvl(child.getTotalFee()));
        tw.setRebateTotal(nvl(child.getRebateTotal()));
        tw.setValidFreight(nvl(child.getValidFreight()));
        tw.setAllocatedFee(BigDecimal.ZERO);
        tw.setProfitCost(BigDecimal.ZERO);
        // 初始快照按当前毛利公式（无分摊成本）计算，随后 rebuildAllocation 统一重算
        tw.setGrossProfit(grossFormulaService.computeGross(tw, Map.of(), grossFormulaService.current()));
        tw.setStatusBeforeLoad(statusBeforeLoad);
        tw.setLoadStatus(TripEnums.LoadStatus.LOADED);
        tw.setLoadTime(LocalDateTime.now());
        tw.setLoadBy(userOrgService.current().userId());
        tw.setLoadByName(operatorName());
        tw.setDeptId(trip.getDeptId());
        tw.setCreateBy(userOrgService.current().userId());
        tw.setUpdateBy(userOrgService.current().userId());
        return tw;
    }

    private NextDest resolveNext(BizTrip trip, BizWaybill parent) {
        List<BizWaybillRouteNode> nodes = nodeMapper.selectList(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .eq(BizWaybillRouteNode::getWaybillId, parent.getId())
                .orderByAsc(BizWaybillRouteNode::getSeq));
        int nextSeq = (parent.getCurrentNodeSeq() == null ? 0 : parent.getCurrentNodeSeq()) + 1;
        BizWaybillRouteNode node = nodes.stream()
                .filter(n -> Objects.equals(n.getSeq(), nextSeq))
                .findFirst().orElse(null);
        if (node == null || node.getToDeptId() == null) {
            return null;
        }
        BizTripDest dest = destMapper.selectOne(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, trip.getId())
                .eq(BizTripDest::getDestDeptId, node.getToDeptId())
                .last("LIMIT 1"));
        return dest == null ? null : new NextDest(dest, node);
    }

    private void writeWaybillLog(BizWaybill parent, BizWaybill child, int q, int remaining) {
        try {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("waybillNo", parent.getWaybillNo());
            before.put("quantity", parent.getQuantity());
            before.put("remainingQuantity", q + remaining);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("childWaybillNo", child.getWaybillNo());
            after.put("childQuantity", q);
            after.put("remainingQuantity", remaining);
            BizWaybillLog log = new BizWaybillLog();
            log.setWaybillId(parent.getId());
            log.setWaybillNo(parent.getWaybillNo());
            log.setActionType(1);
            log.setBeforeSnapshot(objectMapper.writeValueAsString(before));
            log.setAfterSnapshot(objectMapper.writeValueAsString(after));
            log.setReason("装车拆单");
            log.setAuditStatus(0);
            log.setOperatorId(userOrgService.current().userId());
            log.setOperatorName(operatorName());
            log.setDeptId(parent.getDeptId());
            waybillLogMapper.insert(log);
        } catch (Exception ignored) {
            // 日志失败不影响拆单主流程
        }
    }

    private int countRouteNodes(Long waybillId) {
        return Math.toIntExact(nodeMapper.selectCount(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .eq(BizWaybillRouteNode::getWaybillId, waybillId)));
    }

    private void requireLoadingStatus(BizTrip trip) {
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException("当前车次状态不可拆单装车");
        }
    }

    private String operatorName() {
        Long userId = userOrgService.current().userId();
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        return user != null && StringUtils.hasText(user.getRealName()) ? user.getRealName() : "system";
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static class ChildAgg {
        private int totalQuantity;
        private int childCount;
        private int remainingQuantity;
        private BigDecimal remainingWeight;
        private BigDecimal remainingVolume;
        private BigDecimal remainingDelivery;
        private BigDecimal remainingPrepaid;
        private BigDecimal remainingCollect;
        private BigDecimal remainingReturn;
        private BigDecimal remainingMonthly;
        private BigDecimal remainingCash;
        private BigDecimal remainingOwe;

        private ChildAgg() {
            // 初始余额取母单原值，构造后由 aggregate 注入
        }
    }

    private record NextDest(BizTripDest dest, BizWaybillRouteNode node) {
    }

    private record MergeResult(BizWaybill waybill, BizTripWaybill tripWaybill) {
    }
}
