package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.yunshu.modules.transport.mapper.BizTripNoSeqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 车次号生成：CT + yyyyMMdd + 3 位日内流水。
 * 取号与车次插入必须同一事务；并发安全依赖 biz_trip_no_seq 单行原子自增，允许跳号、不允许重号。
 */
@Service
@RequiredArgsConstructor
@DS("master")
public class TripNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BizTripNoSeqMapper seqMapper;

    public String nextTripNo() {
        seqMapper.allocate();
        long current = seqMapper.lastValue();
        return "CT" + LocalDate.now(ZONE).format(DAY) + String.format("%03d", current);
    }
}
