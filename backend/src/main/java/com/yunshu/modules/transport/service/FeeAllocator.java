package com.yunshu.modules.transport.service;

import com.yunshu.common.exception.BizException;
import com.yunshu.modules.transport.constant.TripEnums;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 费用分摊标准函数（方案 4.2）：入参总额、配载单元基数、全车统一分摊方式，出参逐单元金额。
 * 纯计算、不查库、不依赖 Spring；所有 BigDecimal、HALF_UP。
 * 尾差按家伟硬约束归最后一票（配载明细 id 升序最后一张），保证 Σ金额 = 总额。
 */
public final class FeeAllocator {

    private FeeAllocator() {
    }

    /** 配载单元基数：id 用于定位尾差吸收票；chargeWeight 为方式 3 的计费重量 */
    public record Unit(long id, BigDecimal weight, BigDecimal volume, BigDecimal chargeWeight) {
    }

    public record Result(long unitId, BigDecimal basis, BigDecimal basisTotal,
                         BigDecimal ratio, BigDecimal amount) {
    }

    /** 按方式取单票基数：1=重量 2=体积 3=重泡比计费重量 */
    public static BigDecimal basis(Unit unit, int method) {
        BigDecimal value = switch (method) {
            case TripEnums.AllocMethod.WEIGHT -> unit.weight();
            case TripEnums.AllocMethod.VOLUME -> unit.volume();
            case TripEnums.AllocMethod.CHARGE_WEIGHT -> unit.chargeWeight();
            default -> throw new BizException("分摊方式不合法");
        };
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 逐票分摊。调用方必须按配载明细 id 升序传入，尾差由最后一票吸收。
     * 基数为 0 且总额不为 0 时按 D14 阻断；总额为 0 时仍生成 0 金额行，便于对账完整。
     */
    public static List<Result> allocate(BigDecimal total, List<Unit> units, int method) {
        List<Result> results = new ArrayList<>();
        if (units == null || units.isEmpty()) {
            return results;
        }
        BigDecimal amountTotal = total == null ? BigDecimal.ZERO : total;
        BigDecimal basisTotal = units.stream()
                .map(unit -> basis(unit, method))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (basisTotal.signum() == 0) {
            if (amountTotal.signum() != 0) {
                throw new BizException("分摊基数合计为 0，请补充运单重量/体积后重试");
            }
            for (Unit unit : units) {
                results.add(new Result(unit.id(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            }
            return results;
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (Unit unit : units) {
            BigDecimal basis = basis(unit, method);
            BigDecimal ratio = basis.divide(basisTotal, 8, RoundingMode.HALF_UP);
            BigDecimal amount = amountTotal.multiply(basis).divide(basisTotal, 2, RoundingMode.HALF_UP);
            results.add(new Result(unit.id(), basis, basisTotal, ratio, amount));
            allocated = allocated.add(amount);
        }
        BigDecimal diff = amountTotal.subtract(allocated);
        if (diff.signum() != 0) {
            Result last = results.get(results.size() - 1);
            results.set(results.size() - 1, new Result(last.unitId(), last.basis(), last.basisTotal(),
                    last.ratio(), last.amount().add(diff)));
        }
        return results;
    }
}
