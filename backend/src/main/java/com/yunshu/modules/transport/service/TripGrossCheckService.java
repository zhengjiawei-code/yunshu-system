package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.vo.GrossCheckVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 车次毛利对账（#37）：车次毛利 = Σ票毛利 = 收入 − 返款 − 成本；差额非 0 即分摊异常。
 */
@Service
@RequiredArgsConstructor
public class TripGrossCheckService {

    private final TripService tripService;
    private final TripFeeService tripFeeService;

    @DS("slave")
    public GrossCheckVO check(Long tripId) {
        BizTrip trip = tripService.requireVisible(tripId);
        List<BizTripWaybill> rows = tripFeeService.rows(tripId);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal rebate = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal sumTicketGross = BigDecimal.ZERO;
        for (BizTripWaybill row : rows) {
            income = income.add(nvl(row.getTotalFee()));
            rebate = rebate.add(nvl(row.getRebateTotal()));
            cost = cost.add(nvl(row.getProfitCost()));
            sumTicketGross = sumTicketGross.add(nvl(row.getGrossProfit()));
        }
        BigDecimal tripGross = nvl(trip.getGrossProfit());
        // 对账口径：按当前运单毛利公式逐票计算后汇总，与车次毛利比较
        BigDecimal calcGross = sumTicketGross;
        BigDecimal diff = tripGross.subtract(calcGross);

        GrossCheckVO vo = new GrossCheckVO();
        vo.setTripId(tripId);
        vo.setTripGross(tripGross);
        vo.setSumTicketGross(sumTicketGross);
        vo.setIncomeTotal(income);
        vo.setRebateTotal(rebate);
        vo.setCostTotal(cost);
        vo.setCalcGross(calcGross);
        vo.setDiff(diff);
        vo.setBalanced(diff.signum() == 0 && tripGross.compareTo(sumTicketGross) == 0);
        return vo;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
