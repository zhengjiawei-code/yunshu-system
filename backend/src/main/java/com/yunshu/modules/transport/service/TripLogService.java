package com.yunshu.modules.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.modules.transport.entity.BizTripLog;
import com.yunshu.modules.transport.mapper.BizTripLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 车次操作日志：只新增不修改；快照统一序列化为 JSON 文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripLogService {

    private final BizTripLogMapper logMapper;
    private final ObjectMapper objectMapper;

    public void record(Long tripId, String tripNo, int actionType, Long destId,
                       Long deptId, Long operatorId, String operatorName,
                       Map<String, Object> before, Map<String, Object> after, String reason) {
        BizTripLog log = new BizTripLog();
        log.setTripId(tripId);
        log.setTripNo(tripNo);
        log.setActionType(actionType);
        log.setDestId(destId);
        log.setBeforeSnapshot(toJson(before));
        log.setAfterSnapshot(toJson(after));
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setDeptId(deptId);
        logMapper.insert(log);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("车次日志快照序列化失败", ex);
            return null;
        }
    }
}
