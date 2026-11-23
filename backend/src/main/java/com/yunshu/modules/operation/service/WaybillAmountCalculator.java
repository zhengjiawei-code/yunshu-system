package com.yunshu.modules.operation.service;

import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.dto.WaybillSaveRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额派生唯一入口（方案 5.1/5.2/5.4）。
 * 全部使用 BigDecimal、HALF_UP 保留两位；禁止 double。
 * 客户端传入 total_fee/rebate_total/valid_freight/四项支付金额一律不采信。
 */
@Component
public class WaybillAmountCalculator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public record AmountResult(
            BigDecimal totalFee,
            BigDecimal rebateTotal,
            BigDecimal validFreight,
            BigDecimal prepaidFee,
            BigDecimal collectFee,
            BigDecimal monthlyFee,
            BigDecimal returnFee
    ) {
    }

    public AmountResult calculate(WaybillSaveRequest request) {
        BigDecimal freight = money(request.getFreightFee(), "运费");
        BigDecimal delivery = money(request.getDeliveryFee(), "送货费");
        BigDecimal pickup = money(request.getPickupFee(), "提货费");
        BigDecimal other = money(request.getOtherFee(), "其他费");
        BigDecimal receipt = money(request.getReceiptFee(), "回单费");
        BigDecimal makeBill = money(request.getMakeBillFee(), "制单费");
        BigDecimal insurance = money(request.getInsuranceFee(), "保费");
        BigDecimal cashBack = money(request.getCashBack(), "现返");
        BigDecimal oweBack = money(request.getOweBack(), "欠返");
        BigDecimal declared = money(request.getDeclaredValue(), "声明价值");
        if (declared.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new BizException("声明价值超出允许范围");
        }

        BigDecimal total = freight.add(delivery).add(pickup).add(other)
                .add(receipt).add(makeBill).add(insurance).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rebate = cashBack.add(oweBack).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valid = total.subtract(rebate).setScale(2, RoundingMode.HALF_UP);

        Integer payMethod = request.getPayMethod();
        if (payMethod == null) {
            throw new BizException("请选择支付方式");
        }
        if (payMethod < 1 || payMethod > 5) {
            throw new BizException("支付方式码值非法");
        }

        BigDecimal prepaid = ZERO;
        BigDecimal collect = ZERO;
        BigDecimal monthly = ZERO;
        BigDecimal returnFee = ZERO;
        switch (payMethod) {
            case 1 -> prepaid = total;
            case 2 -> collect = total;
            case 3 -> monthly = total;
            case 4 -> returnFee = total;
            case 5 -> {
                prepaid = nonNegative(request.getPrepaidFee(), "现付运费");
                collect = nonNegative(request.getCollectFee(), "到付运费");
                monthly = nonNegative(request.getMonthlyFee(), "月结运费");
                returnFee = nonNegative(request.getReturnFee(), "回付运费");
                BigDecimal sum = prepaid.add(collect).add(monthly).add(returnFee)
                        .setScale(2, RoundingMode.HALF_UP);
                if (sum.compareTo(total) != 0) {
                    BigDecimal diff = total.subtract(sum).setScale(2, RoundingMode.HALF_UP);
                    throw new BizException("支付方式为多笔付时，现付/到付/月结/回付四项合计必须等于运费合计，当前差额 "
                            + diff.toPlainString());
                }
            }
            default -> throw new BizException("支付方式码值非法");
        }
        return new AmountResult(total, rebate, valid, prepaid, collect, monthly, returnFee);
    }

    /** 费用项与返款项：空按 0，负值拒绝，超过 DECIMAL(12,2) 上限拒绝 */
    private BigDecimal money(BigDecimal value, String label) {
        if (value == null) {
            return ZERO;
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0) {
            throw new BizException(label + "不能为负数");
        }
        if (normalized.compareTo(MAX_AMOUNT) > 0) {
            throw new BizException(label + "超出允许范围");
        }
        return normalized;
    }

    private BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal normalized = value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0) {
            throw new BizException(label + "不能为负数");
        }
        return normalized;
    }
}
