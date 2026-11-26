package com.yunshu.modules.transport.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 运单库存推进统一入口：开单初始化、卸车落网点、取消卸车回退都走这里，
 * 避免各模块各写一套 current_hub/load_status 口径。
 */
@Service
@RequiredArgsConstructor
@DS("master")
public class WaybillInventoryService {

    private final BizWaybillMapper waybillMapper;

    /** 开单初始化：库存落在始发网点，节点序号 0，待配载 */
    public void initialize(Long waybillId, Long originHubId, String originHubName, LocalDateTime inventoryTime) {
        waybillMapper.update(null, Wrappers.<BizWaybill>lambdaUpdate()
                .eq(BizWaybill::getId, waybillId)
                .set(BizWaybill::getCurrentHubId, originHubId)
                .set(BizWaybill::getCurrentHubName, originHubName)
                .set(BizWaybill::getCurrentNodeSeq, 0)
                .set(BizWaybill::getInventoryTime, inventoryTime)
                .set(BizWaybill::getLoadStatus, 0));
    }

    /** 卸车落网点：更新当前网点、已完成节点与库存时间，并回到待配载 */
    public void moveToHub(Long waybillId, Long hubId, String hubName, int nodeSeq, LocalDateTime inventoryTime) {
        waybillMapper.update(null, Wrappers.<BizWaybill>lambdaUpdate()
                .eq(BizWaybill::getId, waybillId)
                .set(BizWaybill::getCurrentHubId, hubId)
                .set(BizWaybill::getCurrentHubName, hubName)
                .set(BizWaybill::getCurrentNodeSeq, nodeSeq)
                .set(BizWaybill::getInventoryTime, inventoryTime == null ? LocalDateTime.now() : inventoryTime)
                .set(BizWaybill::getLoadStatus, 0));
    }

    /** 仅更新配载状态（配载/发车/到站/卸车） */
    public void updateLoadStatus(Long waybillId, int loadStatus) {
        waybillMapper.update(null, Wrappers.<BizWaybill>lambdaUpdate()
                .eq(BizWaybill::getId, waybillId)
                .set(BizWaybill::getLoadStatus, loadStatus));
    }
}
