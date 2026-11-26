package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.transport.constant.TripEnums;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripDest;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.mapper.BizTripDestMapper;
import com.yunshu.modules.transport.vo.TripPrintVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 打印运输清单数据（#18）：表头 + 单元清单（含母子关系）+ 合计，不接模板。
 * 可选按子车次（biz_trip_dest.id）过滤，供子车次装车单打印。
 */
@Service
@RequiredArgsConstructor
public class TripPrintService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TripService tripService;
    private final TripFeeService tripFeeService;
    private final BizTripDestMapper destMapper;

    @DS("slave")
    public TripPrintVO print(Long tripId, Long subTripId) {
        BizTrip trip = tripService.requireVisible(tripId);
        List<BizTripDest> dests = destMapper.selectList(Wrappers.<BizTripDest>lambdaQuery()
                .eq(BizTripDest::getTripId, tripId)
                .orderByAsc(BizTripDest::getSeq));
        if (subTripId != null) {
            boolean exists = dests.stream().anyMatch(dest -> Objects.equals(dest.getId(), subTripId));
            if (!exists) {
                throw new com.yunshu.common.exception.BizException(403, "无权访问该子车次");
            }
        }
        List<BizTripWaybill> rows = tripFeeService.rows(tripId);
        if (subTripId != null) {
            rows = rows.stream().filter(row -> Objects.equals(row.getTripDestId(), subTripId)).toList();
        }

        TripPrintVO vo = new TripPrintVO();
        TripPrintVO.Header header = new TripPrintVO.Header();
        header.setTripId(trip.getId());
        header.setTripNo(trip.getTripNo());
        header.setTripName(trip.getTripName());
        header.setTripStatus(trip.getTripStatus());
        header.setOriginDeptName(trip.getOriginDeptName());
        header.setDestNames(dests.stream()
                .filter(dest -> subTripId == null || Objects.equals(dest.getId(), subTripId))
                .map(BizTripDest::getDestDeptName)
                .collect(Collectors.joining("、")));
        header.setPlateNo(trip.getPlateNo());
        header.setVehicleType(trip.getVehicleType());
        header.setVehicleLoad(trip.getVehicleLoad());
        header.setVehicleVolume(trip.getVehicleVolume());
        header.setDriverName(trip.getDriverName());
        header.setDriverPhone(trip.getDriverPhone());
        header.setPlanDepartTime(format(trip.getPlanDepartTime()));
        header.setDepartTime(format(trip.getDepartTime()));
        header.setPlanArriveTime(format(trip.getPlanArriveTime()));
        header.setAllocMethodName(allocMethodName(trip.getAllocMethod()));
        vo.setHeader(header);

        List<TripPrintVO.Row> printRows = rows.stream().map(this::toRow).toList();
        vo.setRows(printRows);

        TripPrintVO.Totals totals = new TripPrintVO.Totals();
        totals.setUnitCount(printRows.size());
        totals.setQuantity(printRows.stream().mapToInt(row -> row.getQuantity() == null ? 0 : row.getQuantity()).sum());
        totals.setWeight(sum(printRows, TripPrintVO.Row::getWeight));
        totals.setVolume(sum(printRows, TripPrintVO.Row::getVolume));
        totals.setTotalFee(sum(printRows, TripPrintVO.Row::getTotalFee));
        totals.setRebateTotal(sum(printRows, TripPrintVO.Row::getRebateTotal));
        totals.setValidFreight(sum(printRows, TripPrintVO.Row::getValidFreight));
        vo.setTotals(totals);
        return vo;
    }

    private TripPrintVO.Row toRow(BizTripWaybill item) {
        TripPrintVO.Row row = new TripPrintVO.Row();
        row.setTripWaybillId(item.getId());
        row.setWaybillNo(item.getWaybillNo());
        row.setParentNo(item.getParentNo());
        row.setIsChild(item.getIsChild());
        row.setSubTripNo(item.getSubTripNo());
        row.setDestHubName(item.getDestHubName());
        row.setDestName(item.getDestName());
        row.setCustomerName(item.getCustomerName());
        row.setReceiverName(item.getReceiverName());
        row.setReceiverPhone(item.getReceiverPhone());
        row.setGoodsName(item.getGoodsName());
        row.setQuantity(item.getQuantity());
        row.setWeight(item.getWeight());
        row.setVolume(item.getVolume());
        row.setPrepaidFee(item.getPrepaidFee());
        row.setCollectFee(item.getCollectFee());
        row.setReturnFee(item.getReturnFee());
        row.setMonthlyFee(item.getMonthlyFee());
        row.setTotalFee(item.getTotalFee());
        row.setRebateTotal(item.getRebateTotal());
        row.setValidFreight(item.getValidFreight());
        return row;
    }

    private BigDecimal sum(List<TripPrintVO.Row> rows,
                           java.util.function.Function<TripPrintVO.Row, BigDecimal> getter) {
        return rows.stream().map(getter).map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
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
}
