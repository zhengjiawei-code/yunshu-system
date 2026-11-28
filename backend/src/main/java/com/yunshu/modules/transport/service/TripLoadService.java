package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.common.filter.TextFilterMode;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.operation.mapper.BizWaybillRouteNodeMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.dto.LoadRequest;
import com.yunshu.modules.transport.dto.LoadedQuery;
import com.yunshu.modules.transport.dto.PoolQuery;
import com.yunshu.modules.transport.dto.SplitRequest;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import com.yunshu.modules.transport.vo.LoadResultVO;
import com.yunshu.modules.transport.vo.LoadedPageVO;
import com.yunshu.modules.transport.vo.PoolPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * B2 配载核心：待配载池、已配载列表、批量配载、批量移出。
 * 池查询一次做足资格校验；配载/移出只做并发条件更新，不再重复做资格校验。
 */
@Service
@RequiredArgsConstructor
public class TripLoadService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int VOIDED = 900;
    private static final int MAX_PAGE_SIZE = 200;

    private final TripService tripService;
    private final BizTripMapper tripMapper;
    private final BizTripDestMapper destMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizWaybillMapper waybillMapper;
    private final BizWaybillRouteNodeMapper nodeMapper;
    private final TripSummaryService summaryService;
    private final TripFeeService tripFeeService;
    private final TripSplitService splitService;
    private final GrossFormulaService grossFormulaService;
    private final TripLogService tripLogService;
    private final UserOrgService userOrgService;
    private final SysUserMapper userMapper;
    private final PlatformTransactionManager transactionManager;

    /* ---------------- 待配载池 ---------------- */

    @DS("slave")
    public PoolPageVO pool(Long tripId, PoolQuery query) {
        BizTrip trip = tripService.requireVisible(tripId);
        requireLoadingStatus(trip);
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId));
        Set<Long> destDeptIds = dests.stream().map(BizTripDest::getDestDeptId).collect(Collectors.toSet());

        QueryWrapper<BizWaybill> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("current_hub_id", trip.getOriginDeptId())
                .eq("load_status", TripEnums.LoadStatus.PENDING)
                .ne("is_child", 1)
                .in("status", 20, 60);
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.in("status", query.getStatus());
        }
        applyLike(wrapper, "waybill_no", query.getWaybillNo());
        applyLike(wrapper, "customer_name", query.getCustomerName());
        applyLike(wrapper, "receiver_name", query.getReceiverName());
        applyRange(wrapper, "inventory_time", query.getInventoryStart(), query.getInventoryEnd());
        applyRange(wrapper, "create_time", query.getCreateStart(), query.getCreateEnd());
        wrapper.orderByAsc("inventory_time").orderByAsc("id");

        List<BizWaybill> candidates = waybillMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return emptyPool(query);
        }
        List<Long> ids = candidates.stream().map(BizWaybill::getId).toList();

        Map<Long, ChildStat> childStats = childStats(ids);
        Set<Long> loadedIds = activeLoadedWaybillIds(ids);
        Map<Long, Map<Integer, BizWaybillRouteNode>> nodeMap = routeNodeMap(ids);

        List<PoolPageVO.Row> rows = new ArrayList<>();
        for (BizWaybill wb : candidates) {
            if (loadedIds.contains(wb.getId())) {
                continue;
            }
            ChildStat stat = childStats.get(wb.getId());
            int unitType = 1;
            int remainingQty = nvl(wb.getQuantity());
            BigDecimal remainingWeight = nvl(wb.getWeight());
            BigDecimal remainingVolume = nvl(wb.getVolume());
            int childCount = 0;
            BigDecimal splitRatio = BigDecimal.ONE;
            if (Objects.equals(wb.getIsChild(), 1)) {
                unitType = 3;
            } else if (stat != null && stat.childCount > 0) {
                childCount = stat.childCount;
                unitType = 2;
                remainingQty = nvl(wb.getQuantity()) - stat.quantity;
                remainingWeight = nvl(wb.getWeight()).subtract(stat.weight).max(BigDecimal.ZERO);
                remainingVolume = nvl(wb.getVolume()).subtract(stat.volume).max(BigDecimal.ZERO);
                splitRatio = ratio(remainingQty, nvl(wb.getQuantity()));
                if (remainingQty <= 0) {
                    continue;
                }
            }

            BizWaybillRouteNode next = nextNode(nodeMap.get(wb.getId()), nvl(wb.getCurrentNodeSeq()));
            if (next == null || next.getToDeptId() == null || !destDeptIds.contains(next.getToDeptId())) {
                continue;
            }
            if (query.getUnitType() != null && query.getUnitType() > 0 && query.getUnitType() != unitType) {
                continue;
            }
            if (query.getNextDeptId() != null && !query.getNextDeptId().equals(next.getToDeptId())) {
                continue;
            }
            if (StringUtils.hasText(query.getDestStation()) && !contains(wb.getDestName(), query.getDestStation())) {
                continue;
            }
            if (!inRange(remainingQty, query.getQuantityMin(), query.getQuantityMax())) {
                continue;
            }
            if (!inRange(remainingWeight, decimal(query.getWeightMin()), decimal(query.getWeightMax()))) {
                continue;
            }
            if (!inRange(remainingVolume, decimal(query.getVolumeMin()), decimal(query.getVolumeMax()))) {
                continue;
            }

            PoolPageVO.Row row = new PoolPageVO.Row();
            row.setWaybillId(wb.getId());
            row.setWaybillNo(wb.getWaybillNo());
            row.setUnitType(unitType);
            row.setParentNo(wb.getParentNo());
            row.setChildCount(childCount);
            row.setSplitRatio(splitRatio);
            row.setStatus(wb.getStatus());
            row.setCustomerName(wb.getCustomerName());
            row.setReceiverName(wb.getReceiverName());
            row.setReceiverPhone(wb.getReceiverPhone());
            row.setGoodsName(wb.getGoodsName());
            row.setDestName(wb.getDestName());
            row.setNextDeptId(next.getToDeptId());
            row.setNextDeptName(next.getToDeptName());
            row.setOriginalQuantity(wb.getQuantity());
            row.setOriginalWeight(wb.getWeight());
            row.setOriginalVolume(wb.getVolume());
            row.setRemainingQuantity(remainingQty);
            row.setRemainingWeight(remainingWeight);
            row.setRemainingVolume(remainingVolume);
            row.setTotalFee(nvl(wb.getTotalFee()));
            row.setRemainFee(unitType == 2 && stat != null
                    ? nvl(wb.getTotalFee()).subtract(stat.totalFee).max(BigDecimal.ZERO)
                    : nvl(wb.getTotalFee()));
            row.setRebateTotal(nvl(wb.getRebateTotal()));
            row.setValidFreight(nvl(wb.getValidFreight()));
            row.setInventoryTime(wb.getInventoryTime());
            row.setCreateTime(wb.getCreateTime());
            rows.add(row);
        }

        sortPool(rows, query.getSortBy(), query.getSortOrder());
        PoolPageVO.Summary summary = new PoolPageVO.Summary();
        summary.setUnitCount(rows.size());
        summary.setQuantity(rows.stream().mapToLong(PoolPageVO.Row::getRemainingQuantity).sum());
        summary.setWeight(rows.stream().map(PoolPageVO.Row::getRemainingWeight).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setVolume(rows.stream().map(PoolPageVO.Row::getRemainingVolume).reduce(BigDecimal.ZERO, BigDecimal::add));

        int pageNum = Math.max(query.getPage(), 1);
        int pageSize = query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());

        PoolPageVO vo = new PoolPageVO();
        vo.setRecords(rows.subList(from, to));
        vo.setTotal(rows.size());
        vo.setPage(pageNum);
        vo.setPageSize(pageSize);
        vo.setSummary(summary);
        return vo;
    }

    private PoolPageVO emptyPool(PoolQuery query) {
        PoolPageVO vo = new PoolPageVO();
        vo.setRecords(List.of());
        vo.setTotal(0);
        vo.setPage(Math.max(query.getPage(), 1));
        vo.setPageSize(query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), MAX_PAGE_SIZE));
        vo.setSummary(new PoolPageVO.Summary());
        return vo;
    }

    /* ---------------- 已配载列表 ---------------- */

    @DS("slave")
    public LoadedPageVO loaded(Long tripId, LoadedQuery query) {
        BizTrip trip = tripService.requireVisible(tripId);
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId));
        Map<Long, BizTripDest> destMap = dests.stream()
                .collect(Collectors.toMap(BizTripDest::getId, Function.identity()));

        int pageNum = Math.max(query.getPage(), 1);
        int pageSize = query.getPageSize() < 1 ? 50 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);

        List<Long> waybillFilterIds = waybillMatchedIds(tripId, query);
        long total = tripWaybillMapper.selectCount(loadedFilter(tripId, query, dests, waybillFilterIds));
        LoadedPageVO.Summary summary = loadedSummary(tripId, query, dests, waybillFilterIds);

        List<LoadedPageVO.Row> rows = new ArrayList<>();
        if (total > 0) {
            QueryWrapper<BizTripWaybill> pageWrapper = loadedFilter(tripId, query, dests, waybillFilterIds);
            pageWrapper.orderByAsc("sub_trip_no").orderByAsc("id");
            pageWrapper.last("LIMIT " + ((long) (pageNum - 1) * pageSize) + "," + pageSize);
            List<BizTripWaybill> pageRows = tripWaybillMapper.selectList(pageWrapper);
            if (!pageRows.isEmpty()) {
                Map<Long, BizWaybill> waybillMap = waybillMapper.selectBatchIds(
                                pageRows.stream().map(BizTripWaybill::getWaybillId).distinct().toList())
                        .stream().collect(Collectors.toMap(BizWaybill::getId, Function.identity()));
                for (BizTripWaybill tw : pageRows) {
                    BizWaybill wb = waybillMap.get(tw.getWaybillId());
                    BizTripDest dest = destMap.get(tw.getTripDestId());
                    LoadedPageVO.Row row = new LoadedPageVO.Row();
                    row.setTripWaybillId(tw.getId());
                    row.setTripDestId(tw.getTripDestId());
                    row.setSubTripNo(tw.getSubTripNo());
                    row.setDestDeptId(dest == null ? null : dest.getDestDeptId());
                    row.setDestDeptName(dest == null ? tw.getDestHubName() : dest.getDestDeptName());
                    row.setWaybillId(tw.getWaybillId());
                    row.setWaybillNo(tw.getWaybillNo());
                    row.setParentNo(tw.getParentNo());
                    row.setIsChild(tw.getIsChild());
                    row.setUnitType(Objects.equals(tw.getIsChild(), 1) ? 3 : 1);
                    row.setSplitRatio(tw.getSplitRatio());
                    row.setStatus(wb == null ? null : wb.getStatus());
                    row.setPayMethod(wb == null ? null : wb.getPayMethod());
                    row.setSettleStatus(wb == null ? 0 : settleStatus(wb.getPayMethod(), wb.getPrepaidFee()));
                    row.setLoadStatus(tw.getLoadStatus());
                    row.setCustomerName(tw.getCustomerName());
                    row.setSenderName(wb == null ? null : wb.getSenderName());
                    row.setReceiverName(tw.getReceiverName());
                    row.setDestName(tw.getDestName());
                    row.setGoodsName(tw.getGoodsName());
                    row.setQuantity(tw.getQuantity());
                    row.setWeight(tw.getWeight());
                    row.setVolume(tw.getVolume());
                    row.setPrepaidFee(tw.getPrepaidFee());
                    row.setCollectFee(tw.getCollectFee());
                    row.setReturnFee(tw.getReturnFee());
                    row.setMonthlyFee(tw.getMonthlyFee());
                    // 运费合计/返款合计取运单当前值；装车运费/装车返款取配载快照
                    row.setTotalFee(wb == null ? nvl(tw.getTotalFee()) : nvl(wb.getTotalFee()));
                    row.setRebateTotal(wb == null ? nvl(tw.getRebateTotal()) : nvl(wb.getRebateTotal()));
                    row.setLoadFee(nvl(tw.getTotalFee()));
                    row.setLoadRebate(nvl(tw.getRebateTotal()));
                    row.setValidFreight(tw.getValidFreight());
                    row.setAllocatedFee(tw.getAllocatedFee());
                    row.setProfitCost(tw.getProfitCost());
                    row.setGrossProfit(tw.getGrossProfit());
                    row.setLoadTime(tw.getLoadTime());
                    rows.add(row);
                }
            }
        }

        LoadedPageVO vo = new LoadedPageVO();
        vo.setRecords(rows);
        vo.setTotal(total);
        vo.setPage(pageNum);
        vo.setPageSize(pageSize);
        vo.setSummary(summary);
        return vo;
    }

    /** 已配载筛选：所有条件先落 SQL，再分页。 */
    private QueryWrapper<BizTripWaybill> loadedFilter(Long tripId, LoadedQuery query,
                                                      List<BizTripDest> dests, List<Long> waybillFilterIds) {
        QueryWrapper<BizTripWaybill> wrapper = new QueryWrapper<>();
        wrapper.eq("trip_id", tripId);
        if (waybillFilterIds != null) {
            if (waybillFilterIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("waybill_id", waybillFilterIds);
            }
        }
        if (query.getTripDestId() != null) {
            wrapper.eq("trip_dest_id", query.getTripDestId());
        } else if (query.getDestDeptId() != null) {
            List<Long> destIds = dests.stream()
                    .filter(item -> Objects.equals(item.getDestDeptId(), query.getDestDeptId()))
                    .map(BizTripDest::getId)
                    .toList();
            if (destIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("trip_dest_id", destIds);
            }
        }
        if (query.getLoadStatus() != null && query.getLoadStatus() > 0) {
            wrapper.eq("load_status", query.getLoadStatus());
        }
        if (query.getUnitType() != null && query.getUnitType() > 0) {
            wrapper.eq("is_child", query.getUnitType() == 3 ? 1 : 0);
        }
        applyTwLike(wrapper, "waybill_no", query.getWaybillNo());
        applyTwLike(wrapper, "parent_no", query.getParentNo());
        applyTwLike(wrapper, "customer_name", query.getCustomerName());
        applyTwLike(wrapper, "receiver_name", query.getReceiverName());
        applyTwLike(wrapper, "dest_name", query.getDestName());
        applyRange(wrapper, "quantity", query.getQuantityMin(), query.getQuantityMax());
        applyRange(wrapper, "weight", query.getWeightMin(), query.getWeightMax());
        applyRange(wrapper, "volume", query.getVolumeMin(), query.getVolumeMax());
        applyRange(wrapper, "total_fee", query.getLoadFeeMin(), query.getLoadFeeMax());
        applyRange(wrapper, "rebate_total", query.getLoadRebateMin(), query.getLoadRebateMax());
        applyTimeRange(wrapper, "load_time", query.getLoadStart(), query.getLoadEnd());
        return wrapper;
    }

    /** 运单级筛选先查 id，再以 IN 落 SQL；不使用子查询（数据权限拦截禁止 WHERE 子查询）。 */
    private List<Long> waybillMatchedIds(Long tripId, LoadedQuery query) {
        boolean hasWaybillFilter = (query.getStatus() != null && !query.getStatus().isEmpty())
                || (query.getPayMethod() != null && !query.getPayMethod().isEmpty())
                || (query.getSettleStatus() != null && query.getSettleStatus() >= 0)
                || StringUtils.hasText(query.getSenderName())
                || query.getTotalFeeMin() != null || query.getTotalFeeMax() != null
                || query.getRebateTotalMin() != null || query.getRebateTotalMax() != null;
        if (!hasWaybillFilter) {
            return null;
        }
        List<Long> tripWaybillIds = tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                        .select(BizTripWaybill::getWaybillId)
                        .eq(BizTripWaybill::getTripId, tripId))
                .stream().map(BizTripWaybill::getWaybillId).distinct().toList();
        if (tripWaybillIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<BizWaybill> wrapper = new QueryWrapper<>();
        wrapper.select("id").in("id", tripWaybillIds);
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.in("status", query.getStatus());
        }
        if (query.getPayMethod() != null && !query.getPayMethod().isEmpty()) {
            wrapper.in("pay_method", query.getPayMethod());
        }
        if (query.getSettleStatus() != null && query.getSettleStatus() >= 0) {
            switch (query.getSettleStatus()) {
                case 1 -> wrapper.eq("pay_method", 1);
                case 2 -> wrapper.eq("pay_method", 5).gt("prepaid_fee", BigDecimal.ZERO);
                default -> wrapper.apply("(pay_method IS NULL OR pay_method IN (2, 3, 4)"
                        + " OR (pay_method = 5 AND COALESCE(prepaid_fee, 0) = 0))");
            }
        }
        if (StringUtils.hasText(query.getSenderName())) {
            wrapper.like("sender_name", query.getSenderName().trim());
        }
        if (query.getTotalFeeMin() != null) {
            wrapper.ge("total_fee", query.getTotalFeeMin());
        }
        if (query.getTotalFeeMax() != null) {
            wrapper.le("total_fee", query.getTotalFeeMax());
        }
        if (query.getRebateTotalMin() != null) {
            wrapper.ge("rebate_total", query.getRebateTotalMin());
        }
        if (query.getRebateTotalMax() != null) {
            wrapper.le("rebate_total", query.getRebateTotalMax());
        }
        return waybillMapper.selectList(wrapper).stream().map(BizWaybill::getId).toList();
    }

    /** 汇总取筛选后的全量，不受分页影响。 */
    private LoadedPageVO.Summary loadedSummary(Long tripId, LoadedQuery query, List<BizTripDest> dests,
                                               List<Long> waybillFilterIds) {
        QueryWrapper<BizTripWaybill> wrapper = loadedFilter(tripId, query, dests, waybillFilterIds);
        wrapper.select(
                "COUNT(*) AS unit_count",
                "COALESCE(SUM(quantity), 0) AS total_quantity",
                "COALESCE(SUM(weight), 0) AS total_weight",
                "COALESCE(SUM(volume), 0) AS total_volume",
                "COALESCE(SUM(total_fee), 0) AS total_fee",
                "COALESCE(SUM(rebate_total), 0) AS total_rebate",
                "COALESCE(SUM(gross_profit), 0) AS total_gross");
        Map<String, Object> agg = tripWaybillMapper.selectMaps(wrapper).stream()
                .findFirst().orElse(Map.of());
        LoadedPageVO.Summary summary = new LoadedPageVO.Summary();
        summary.setUnitCount(number(agg.get("unit_count")).longValue());
        summary.setQuantity(number(agg.get("total_quantity")).longValue());
        summary.setWeight(decimal(agg.get("total_weight")));
        summary.setVolume(decimal(agg.get("total_volume")));
        summary.setTotalFee(decimal(agg.get("total_fee")));
        summary.setRebateTotal(decimal(agg.get("total_rebate")));
        summary.setGrossProfit(decimal(agg.get("total_gross")));
        return summary;
    }

    private int settleStatus(Integer payMethod, BigDecimal prepaidFee) {
        if (payMethod == null) {
            return 0;
        }
        return switch (payMethod) {
            case 1 -> 1;
            case 5 -> nvl(prepaidFee).compareTo(BigDecimal.ZERO) > 0 ? 2 : 0;
            default -> 0;
        };
    }

    private Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    /* ---------------- 批量配载 / 移出 ---------------- */

    public LoadResultVO load(Long tripId, LoadRequest request) {
        BizTrip trip = tripService.requireVisible(tripId);
        requireLoadingStatus(trip);

        LoadResultVO result = new LoadResultVO();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (Long id : request.getWaybillIds()) {
            try {
                BizWaybill wb = waybillMapper.selectById(id);
                if (wb == null) {
                    throw new BizException("运单不存在：" + id);
                }
                ChildStat stat = Objects.equals(wb.getIsChild(), 1)
                        ? new ChildStat() : childStats(List.of(id)).getOrDefault(id, new ChildStat());
                if (stat.childCount > 0) {
                    // 母单余额直接配载：按剩余量自动生成子单，不再要求先拆单
                    int remaining = nvl(wb.getQuantity()) - stat.quantity;
                    if (remaining <= 0) {
                        throw new BizException("该母单余额为 0，请刷新");
                    }
                    SplitRequest split = new SplitRequest();
                    split.setParentWaybillId(id);
                    split.setQuantity(remaining);
                    splitService.splitLoad(tripId, split);
                } else {
                    template.executeWithoutResult(status -> loadOne(trip, id));
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BizException ex) {
                result.getFailures().add(failure(id, ex.getMessage()));
            } catch (Exception ex) {
                result.getFailures().add(failure(id, "配载失败：" + ex.getMessage()));
            }
        }
        if (result.getSuccessCount() > 0) {
            summaryService.recalc(tripId);
            tripFeeService.reallocateIfConfigured(tripId);
            BizTrip after = tripMapper.selectById(tripId);
            tripLogService.record(tripId, trip.getTripNo(), 3, null, trip.getDeptId(),
                    userOrgService.current().userId(), operatorName(), null,
                    Map.of("success", result.getSuccessCount(), "fail", result.getFailures().size()), null);
            if (after.getVehicleLoad() != null && nvl(after.getTotalWeight()).compareTo(after.getVehicleLoad()) > 0) {
                result.getWarnings().add("已超核定载重");
            }
            if (after.getVehicleVolume() != null && nvl(after.getTotalVolume()).compareTo(after.getVehicleVolume()) > 0) {
                result.getWarnings().add("已超核定容积");
            }
        }
        return result;
    }

    public LoadResultVO remove(Long tripId, LoadRequest request) {
        BizTrip trip = tripService.requireVisible(tripId);
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException("已发车车次不能移除配载");
        }
        LoadResultVO result = new LoadResultVO();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (Long id : request.getWaybillIds()) {
            try {
                template.executeWithoutResult(status -> removeOne(trip, id));
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BizException ex) {
                result.getFailures().add(failure(id, ex.getMessage()));
            } catch (Exception ex) {
                result.getFailures().add(failure(id, "移出失败：" + ex.getMessage()));
            }
        }
        if (result.getSuccessCount() > 0) {
            summaryService.recalc(tripId);
            tripFeeService.reallocateIfConfigured(tripId);
            long remain = tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                    .eq(BizTripWaybill::getTripId, tripId));
            if (remain == 0 && Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
                tripMapper.update(null, new UpdateWrapper<BizTrip>().eq("id", tripId)
                        .set("trip_status", TripEnums.TripStatus.PLAN));
            }
            tripLogService.record(tripId, trip.getTripNo(), 4, null, trip.getDeptId(),
                    userOrgService.current().userId(), operatorName(), null,
                    Map.of("success", result.getSuccessCount(), "fail", result.getFailures().size()), null);
        }
        return result;
    }

    private void loadOne(BizTrip trip, Long waybillId) {
        BizWaybill wb = waybillMapper.selectByIdForUpdate(waybillId);
        if (wb == null) {
            throw new BizException("运单不存在或已删除");
        }
        if (!Objects.equals(wb.getIsChild(), 1) && childStats(List.of(waybillId))
                .getOrDefault(waybillId, new ChildStat()).childCount > 0) {
            throw new BizException(400, "母单已拆单，剩余件数请使用拆单装车");
        }
        if (!Objects.equals(wb.getLoadStatus(), TripEnums.LoadStatus.PENDING)) {
            throw new BizException(409, "运单已被其他车次配载");
        }
        if (!Objects.equals(wb.getStatus(), 20) && !Objects.equals(wb.getStatus(), 60)) {
            throw new BizException(409, "运单状态已变化，请刷新");
        }
        if (!Objects.equals(wb.getCurrentHubId(), trip.getOriginDeptId())) {
            throw new BizException(409, "运单库存网点已变化，请刷新");
        }
        long active = tripWaybillMapper.selectCount(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getWaybillId, waybillId)
                .in(BizTripWaybill::getLoadStatus, 1, 2, 3));
        if (active > 0) {
            throw new BizException(409, "运单已被其他车次配载");
        }

        List<BizWaybillRouteNode> nodes = nodeMapper.selectList(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .eq(BizWaybillRouteNode::getWaybillId, waybillId)
                .orderByAsc(BizWaybillRouteNode::getSeq));
        BizWaybillRouteNode next = nodes.stream()
                .filter(n -> Objects.equals(n.getSeq(), nvl(wb.getCurrentNodeSeq()) + 1))
                .findFirst().orElse(null);
        if (next == null || next.getToDeptId() == null) {
            throw new BizException(409, "运单下一节点已变化，请刷新");
        }
        BizTripDest dest = destMapper.selectOne(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, trip.getId())
                .eq(BizTripDest::getDestDeptId, next.getToDeptId())
                .last("LIMIT 1"));
        if (dest == null) {
            throw new BizException(409, "运单下一节点不在本车次目的网点内");
        }

        BizTripWaybill tw = snapshot(wb, trip, dest, next);
        tripWaybillMapper.insert(tw);

        int rows = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                .eq("id", waybillId)
                .eq("load_status", TripEnums.LoadStatus.PENDING)
                .in("status", 20, 60)
                .set("status", 70)
                .set("load_status", TripEnums.LoadStatus.LOADED)
                .set("update_by", userOrgService.current().userId()));
        if (rows == 0) {
            throw new BizException(409, "运单已被其他车次配载");
        }
        if (Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)) {
            tripMapper.update(null, new UpdateWrapper<BizTrip>().eq("id", trip.getId())
                    .set("trip_status", TripEnums.TripStatus.LOADING));
            trip.setTripStatus(TripEnums.TripStatus.LOADING);
        }
    }

    private void removeOne(BizTrip trip, Long waybillId) {
        BizTripWaybill tw = tripWaybillMapper.selectOne(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getTripId, trip.getId())
                .eq(BizTripWaybill::getWaybillId, waybillId)
                .last("LIMIT 1"));
        if (tw == null) {
            throw new BizException("该运单不在本车次配载");
        }
        if (!Objects.equals(tw.getLoadStatus(), TripEnums.LoadStatus.LOADED)) {
            throw new BizException("仅已配载未发车可移出");
        }
        int rows = tripWaybillMapper.update(null, new UpdateWrapper<BizTripWaybill>()
                .eq("id", tw.getId())
                .eq("load_status", TripEnums.LoadStatus.LOADED)
                .set("deleted", 1)
                .set("update_by", userOrgService.current().userId()));
        if (rows == 0) {
            throw new BizException(409, "配载明细已被他人修改，请刷新");
        }
        int restored;
        if (Objects.equals(tw.getIsChild(), 1)) {
            // 撤销子单配载：作废子单，件数/金额自动回归母单余额
            restored = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", waybillId)
                    .eq("load_status", TripEnums.LoadStatus.LOADED)
                    .set("status", VOIDED)
                    .set("load_status", TripEnums.LoadStatus.PENDING)
                    .set("update_by", userOrgService.current().userId()));
        } else {
            restored = waybillMapper.update(null, new UpdateWrapper<BizWaybill>()
                    .eq("id", waybillId)
                    .eq("load_status", TripEnums.LoadStatus.LOADED)
                    .set("status", tw.getStatusBeforeLoad())
                    .set("load_status", TripEnums.LoadStatus.PENDING)
                    .set("update_by", userOrgService.current().userId()));
        }
        if (restored == 0) {
            throw new BizException(409, "运单状态已被他人修改，请刷新");
        }
    }

    private BizTripWaybill snapshot(BizWaybill wb, BizTrip trip, BizTripDest dest, BizWaybillRouteNode next) {
        BizTripWaybill tw = new BizTripWaybill();
        tw.setTripId(trip.getId());
        tw.setTripDestId(dest.getId());
        tw.setSubTripNo(dest.getSubTripNo());
        tw.setWaybillId(wb.getId());
        tw.setWaybillNo(wb.getWaybillNo());
        tw.setParentNo(wb.getParentNo());
        tw.setIsChild(wb.getIsChild());
        tw.setRouteNodeSeq(next.getSeq());
        tw.setDestHubId(next.getToDeptId());
        tw.setDestHubName(next.getToDeptName());
        tw.setDestName(wb.getDestName());
        tw.setCustomerName(wb.getCustomerName());
        tw.setReceiverName(wb.getReceiverName());
        tw.setReceiverPhone(wb.getReceiverPhone());
        tw.setGoodsName(wb.getGoodsName());
        tw.setQuantity(wb.getQuantity());
        tw.setWeight(nvl(wb.getWeight()));
        tw.setVolume(nvl(wb.getVolume()));
        tw.setPrepaidFee(nvl(wb.getPrepaidFee()));
        tw.setCollectFee(nvl(wb.getCollectFee()));
        tw.setReturnFee(nvl(wb.getReturnFee()));
        tw.setMonthlyFee(nvl(wb.getMonthlyFee()));
        tw.setTotalFee(nvl(wb.getTotalFee()));
        tw.setRebateTotal(nvl(wb.getRebateTotal()));
        tw.setValidFreight(nvl(wb.getValidFreight()));
        tw.setAllocatedFee(BigDecimal.ZERO);
        tw.setProfitCost(BigDecimal.ZERO);
        // 初始快照按当前毛利公式（无分摊成本）计算，随后 rebuildAllocation 统一重算
        tw.setGrossProfit(grossFormulaService.computeGross(tw, Map.of(), grossFormulaService.current()));
        tw.setStatusBeforeLoad(wb.getStatus());
        tw.setLoadStatus(TripEnums.LoadStatus.LOADED);
        tw.setLoadTime(LocalDateTime.now());
        tw.setLoadBy(userOrgService.current().userId());
        tw.setLoadByName(operatorName());
        tw.setDeptId(trip.getDeptId());
        tw.setCreateBy(userOrgService.current().userId());
        tw.setUpdateBy(userOrgService.current().userId());
        return tw;
    }

    /* ---------------- 内部工具 ---------------- */

    private void requireLoadingStatus(BizTrip trip) {
        if (!Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.PLAN)
                && !Objects.equals(trip.getTripStatus(), TripEnums.TripStatus.LOADING)) {
            throw new BizException("当前车次状态不可配载");
        }
    }

    private Map<Long, ChildStat> childStats(List<Long> parentIds) {
        Map<Long, ChildStat> result = new HashMap<>();
        if (parentIds.isEmpty()) {
            return result;
        }
        List<BizWaybill> children = waybillMapper.selectList(Wrappers.<BizWaybill>lambdaQuery()
                .in(BizWaybill::getParentId, parentIds)
                .eq(BizWaybill::getIsChild, 1)
                .ne(BizWaybill::getStatus, VOIDED)
                // 仅统计仍在车上的子单；已撤销回池的子单不再占用母单余额
                .ne(BizWaybill::getLoadStatus, TripEnums.LoadStatus.PENDING));
        for (BizWaybill child : children) {
            ChildStat stat = result.computeIfAbsent(child.getParentId(), key -> new ChildStat());
            stat.childCount++;
            stat.quantity += nvl(child.getQuantity());
            stat.weight = stat.weight.add(nvl(child.getWeight()));
            stat.volume = stat.volume.add(nvl(child.getVolume()));
            stat.totalFee = stat.totalFee.add(nvl(child.getTotalFee()));
        }
        return result;
    }

    private Set<Long> activeLoadedWaybillIds(List<Long> waybillIds) {
        List<BizTripWaybill> active = tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .in(BizTripWaybill::getWaybillId, waybillIds)
                .in(BizTripWaybill::getLoadStatus, 1, 2, 3));
        if (active.isEmpty()) {
            return Set.of();
        }
        Set<Long> tripIds = active.stream().map(BizTripWaybill::getTripId).collect(Collectors.toSet());
        Set<Long> closedTripIds = tripMapper.selectList(Wrappers.<BizTrip>lambdaQuery()
                        .in(BizTrip::getId, tripIds)
                        .eq(BizTrip::getTripStatus, TripEnums.TripStatus.CLOSED))
                .stream().map(BizTrip::getId).collect(Collectors.toSet());
        Set<Long> result = new HashSet<>();
        for (BizTripWaybill tw : active) {
            if (!closedTripIds.contains(tw.getTripId())) {
                result.add(tw.getWaybillId());
            }
        }
        return result;
    }

    private Map<Long, Map<Integer, BizWaybillRouteNode>> routeNodeMap(List<Long> waybillIds) {
        Map<Long, Map<Integer, BizWaybillRouteNode>> result = new HashMap<>();
        for (BizWaybillRouteNode node : nodeMapper.selectList(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .in(BizWaybillRouteNode::getWaybillId, waybillIds))) {
            result.computeIfAbsent(node.getWaybillId(), key -> new HashMap<>()).put(node.getSeq(), node);
        }
        return result;
    }

    private BizWaybillRouteNode nextNode(Map<Integer, BizWaybillRouteNode> nodes, int currentNodeSeq) {
        return nodes == null ? null : nodes.get(currentNodeSeq + 1);
    }

    private void sortPool(List<PoolPageVO.Row> rows, String sortBy, String sortOrder) {
        Comparator<PoolPageVO.Row> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "waybillNo" -> Comparator.comparing(PoolPageVO.Row::getWaybillNo,
                    Comparator.nullsLast(String::compareTo));
            case "quantity" -> Comparator.comparing(PoolPageVO.Row::getRemainingQuantity,
                    Comparator.nullsLast(Integer::compareTo));
            case "weight" -> Comparator.comparing(PoolPageVO.Row::getRemainingWeight,
                    Comparator.nullsLast(BigDecimal::compareTo));
            case "volume" -> Comparator.comparing(PoolPageVO.Row::getRemainingVolume,
                    Comparator.nullsLast(BigDecimal::compareTo));
            case "createTime" -> Comparator.comparing(PoolPageVO.Row::getCreateTime,
                    Comparator.nullsLast(LocalDateTime::compareTo));
            default -> Comparator.comparing(PoolPageVO.Row::getInventoryTime,
                    Comparator.nullsLast(LocalDateTime::compareTo));
        };
        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator.thenComparing(PoolPageVO.Row::getWaybillId));
    }

    private void applyLike(QueryWrapper<BizWaybill> wrapper, String column, String value) {
        if (StringUtils.hasText(value)) {
            wrapper.like(column, value.trim());
        }
    }

    private void applyTwLike(QueryWrapper<BizTripWaybill> wrapper, String column, String value) {
        if (StringUtils.hasText(value)) {
            wrapper.like(column, value.trim());
        }
    }

    private void applyRange(QueryWrapper<BizTripWaybill> wrapper, String column, Integer min, Integer max) {
        if (min != null && min > 0) {
            wrapper.ge(column, min);
        }
        if (max != null && max > 0) {
            wrapper.le(column, max);
        }
    }

    private void applyRange(QueryWrapper<BizTripWaybill> wrapper, String column, BigDecimal min, BigDecimal max) {
        if (min != null) {
            wrapper.ge(column, min);
        }
        if (max != null) {
            wrapper.le(column, max);
        }
    }

    private void applyTimeRange(QueryWrapper<BizTripWaybill> wrapper, String column, String start, String end) {
        LocalDateTime from = parse(start, false);
        LocalDateTime to = parse(end, true);
        if (from != null) {
            wrapper.ge(column, from);
        }
        if (to != null) {
            wrapper.le(column, to);
        }
    }

    private void applyRange(QueryWrapper<BizWaybill> wrapper, String column, String start, String end) {
        LocalDateTime from = parse(start, false);
        LocalDateTime to = parse(end, true);
        if (from != null) {
            wrapper.ge(column, from);
        }
        if (to != null) {
            wrapper.le(column, to);
        }
    }

    private LocalDateTime parse(String value, boolean end) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 10
                ? (end ? LocalDate.parse(text).atTime(23, 59, 59) : LocalDate.parse(text).atStartOfDay())
                : LocalDateTime.parse(text, DATE_TIME);
    }

    private boolean inRange(Integer value, Integer min, Integer max) {
        if (min == null && max == null) {
            return true;
        }
        int v = value == null ? 0 : value;
        return (min == null || v >= min) && (max == null || v <= max);
    }

    private boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return true;
        }
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return (min == null || v.compareTo(min) >= 0) && (max == null || v.compareTo(max) <= 0);
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean contains(String source, String keyword) {
        return source != null && keyword != null
                && source.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private BigDecimal ratio(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 6, java.math.RoundingMode.HALF_UP);
    }

    private LoadResultVO.Failure failure(Long id, String reason) {
        LoadResultVO.Failure failure = new LoadResultVO.Failure();
        failure.setWaybillId(id);
        failure.setReason(reason);
        return failure;
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

    private static class ChildStat {
        private int childCount;
        private int quantity;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal totalFee = BigDecimal.ZERO;
    }
}
