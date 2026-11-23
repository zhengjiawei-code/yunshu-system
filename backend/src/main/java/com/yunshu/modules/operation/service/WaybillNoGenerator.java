package com.yunshu.modules.operation.service;

import com.yunshu.modules.operation.mapper.WaybillSeqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 运单号生成：YD + yyyyMMdd + 6 位日内流水。
 * 取号与运单插入必须处于同一事务（由调用方 @Transactional 保证），
 * 并发安全依赖 biz_waybill_no_seq 的单行原子自增；允许跳号，不允许重号。
 */
@Service
@RequiredArgsConstructor
public class WaybillNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final WaybillSeqMapper seqMapper;

    public String nextWaybillNo() {
        seqMapper.allocateWaybillNo();
        long current = seqMapper.lastWaybillNo();
        return "YD" + LocalDate.now(ZONE).format(DAY) + String.format("%06d", current);
    }
}
