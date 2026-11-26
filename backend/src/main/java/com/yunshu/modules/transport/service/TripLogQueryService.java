package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.transport.entity.BizTripLog;
import com.yunshu.modules.transport.mapper.BizTripLogMapper;
import com.yunshu.modules.transport.vo.TripLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 车次操作日志查询（#19）：按时间倒序，快照原样返回。 */
@Service
@RequiredArgsConstructor
public class TripLogQueryService {

    private final TripService tripService;
    private final BizTripLogMapper logMapper;

    @DS("slave")
    public List<TripLogVO> logs(Long tripId) {
        tripService.requireVisible(tripId);
        return logMapper.selectList(Wrappers.<BizTripLog>lambdaQuery()
                        .eq(BizTripLog::getTripId, tripId)
                        .orderByDesc(BizTripLog::getCreateTime)
                        .orderByDesc(BizTripLog::getId))
                .stream().map(this::toVO).toList();
    }

    private TripLogVO toVO(BizTripLog log) {
        TripLogVO vo = new TripLogVO();
        vo.setId(log.getId());
        vo.setActionType(log.getActionType());
        vo.setActionName(actionName(log.getActionType()));
        vo.setDestId(log.getDestId());
        vo.setReason(log.getReason());
        vo.setOperatorName(log.getOperatorName());
        vo.setBeforeSnapshot(log.getBeforeSnapshot());
        vo.setAfterSnapshot(log.getAfterSnapshot());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private String actionName(Integer action) {
        if (action == null) {
            return "未知";
        }
        return switch (action) {
            case 1 -> "创建";
            case 2 -> "修改";
            case 3 -> "配载";
            case 4 -> "移除配载";
            case 5 -> "发车";
            case 6 -> "取消发车";
            case 7 -> "到站";
            case 8 -> "完成卸车";
            case 9 -> "关闭";
            case 10 -> "删除";
            case 11 -> "车辆信息变更";
            case 12 -> "费用信息变更";
            case 13 -> "运单改单刷新";
            case 14 -> "运单作废解除配载";
            case 15 -> "目的网点变更";
            case 16 -> "取消到站";
            case 17 -> "取消卸车";
            default -> "未知";
        };
    }
}
