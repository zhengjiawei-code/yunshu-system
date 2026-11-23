package com.yunshu.modules.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.entity.BizPreWaybill;
import com.yunshu.modules.operation.mapper.BizPreWaybillMapper;
import com.yunshu.modules.operation.vo.PreOrderPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预开单只读查询（本阶段占位）：列表与详情仅读，接单/退回/确认揽收留到下一阶段。
 * 数据权限由拦截器自动追加 dept_id 条件。
 */
@Service
@RequiredArgsConstructor
public class PreOrderService {

    private final BizPreWaybillMapper preWaybillMapper;

    public PreOrderPageVO page(Integer status, String keyword, LocalDateTime submitStart, LocalDateTime submitEnd,
                               String sortOrder, long pageNum, long pageSize) {
        long safePage = pageNum < 1 ? 1 : pageNum;
        long safeSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        LambdaQueryWrapper<BizPreWaybill> wrapper = Wrappers.<BizPreWaybill>lambdaQuery();
        if (status != null) {
            wrapper.eq(BizPreWaybill::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(item -> item.like(BizPreWaybill::getPreOrderNo, kw)
                    .or().like(BizPreWaybill::getCustomerName, kw)
                    .or().like(BizPreWaybill::getDestName, kw)
                    .or().like(BizPreWaybill::getSenderName, kw));
        }
        if (submitStart != null) {
            wrapper.ge(BizPreWaybill::getSubmitTime, submitStart);
        }
        if (submitEnd != null) {
            wrapper.le(BizPreWaybill::getSubmitTime, submitEnd);
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        wrapper.orderBy(true, asc, BizPreWaybill::getSubmitTime);

        Page<BizPreWaybill> page = preWaybillMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<BizPreWaybill> all = preWaybillMapper.selectList(wrapper);

        PreOrderPageVO vo = new PreOrderPageVO();
        vo.setData(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setPage(safePage);
        vo.setPageSize(safeSize);
        vo.setSummary(summary(all));
        return vo;
    }

    public BizPreWaybill detail(Long id) {
        BizPreWaybill item = id == null ? null : preWaybillMapper.selectById(id);
        if (item == null) {
            throw new BizException(403, "无权访问该预开单");
        }
        return item;
    }

    private Map<String, BigDecimal> summary(List<BizPreWaybill> rows) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        for (BizPreWaybill item : rows) {
            if (item.getQuantity() != null) {
                quantity = quantity.add(BigDecimal.valueOf(item.getQuantity()));
            }
            if (item.getWeight() != null) {
                weight = weight.add(item.getWeight());
            }
            if (item.getVolume() != null) {
                volume = volume.add(item.getVolume());
            }
        }
        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        summary.put("quantity", quantity);
        summary.put("weight", weight.setScale(3, RoundingMode.HALF_UP));
        summary.put("volume", volume.setScale(3, RoundingMode.HALF_UP));
        return summary;
    }
}
