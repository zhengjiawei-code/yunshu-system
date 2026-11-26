package com.yunshu.modules.transport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.transport.dto.FeeStatusRequest;
import com.yunshu.modules.transport.dto.FeeTypeSaveRequest;
import com.yunshu.modules.transport.dto.FeeTypeUpdateRequest;
import com.yunshu.modules.transport.entity.SysFeeType;
import com.yunshu.modules.transport.mapper.SysFeeTypeMapper;
import com.yunshu.modules.transport.vo.FeeTypeCatalogVO;
import com.yunshu.modules.transport.vo.FeeTypePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 费用类型配置（#26-#30）：收入 4 项为代码级只读虚拟行，其余落 sys_fee_type。
 * 编码创建后不可改；代收/代付保存后强制 profit_enabled=0、方向为空。
 */
@Service
@RequiredArgsConstructor
public class FeeTypeService {

    private static final Map<String, String> INCOME_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> INCOME_FIELDS = new LinkedHashMap<>();

    static {
        INCOME_NAMES.put("INC01", "现付运费");
        INCOME_NAMES.put("INC02", "到付运费");
        INCOME_NAMES.put("INC03", "回付运费");
        INCOME_NAMES.put("INC04", "月结运费");
        INCOME_FIELDS.put("INC01", "biz_waybill.prepaid_fee");
        INCOME_FIELDS.put("INC02", "biz_waybill.collect_fee");
        INCOME_FIELDS.put("INC03", "biz_waybill.return_fee");
        INCOME_FIELDS.put("INC04", "biz_waybill.monthly_fee");
    }

    private final SysFeeTypeMapper feeTypeMapper;

    /* ---------------- #26 列表 ---------------- */

    public FeeTypePageVO page(List<Integer> feeCategory, String feeCode, String feeName,
                              Boolean enabled, Integer page, Integer pageSize) {
        List<FeeTypePageVO.Row> rows = new ArrayList<>();
        boolean includeIncome = feeCategory == null || feeCategory.isEmpty() || feeCategory.contains(1);
        if (includeIncome) {
            int sort = 1;
            for (Map.Entry<String, String> entry : INCOME_NAMES.entrySet()) {
                if (match(feeCode, entry.getKey()) && match(feeName, entry.getValue())
                        && !Boolean.FALSE.equals(enabled)) {
                    FeeTypePageVO.Row row = new FeeTypePageVO.Row();
                    row.setFeeCode(entry.getKey());
                    row.setFeeName(entry.getValue());
                    row.setFeeCategory(1);
                    row.setFeeCategoryName("收入");
                    row.setEnabled(true);
                    row.setProfitEnabled(true);
                    row.setProfitDirection(1);
                    row.setSourceField(INCOME_FIELDS.get(entry.getKey()));
                    row.setSortOrder(sort++);
                    row.setRemark("内置收入项，只读");
                    row.setReadonly(true);
                    rows.add(row);
                }
            }
        }

        LambdaQueryWrapper<SysFeeType> wrapper = Wrappers.<SysFeeType>lambdaQuery();
        if (feeCategory != null && !feeCategory.isEmpty()) {
            List<Integer> nonIncome = feeCategory.stream().filter(value -> value != null && value != 1).toList();
            if (nonIncome.isEmpty()) {
                wrapper.eq(SysFeeType::getId, -1L);
            } else {
                wrapper.in(SysFeeType::getFeeCategory, nonIncome);
            }
        }
        if (StringUtils.hasText(feeCode)) {
            wrapper.like(SysFeeType::getFeeCode, feeCode.trim());
        }
        if (StringUtils.hasText(feeName)) {
            wrapper.like(SysFeeType::getFeeName, feeName.trim());
        }
        if (enabled != null) {
            wrapper.eq(SysFeeType::getEnabled, enabled);
        }
        wrapper.orderByAsc(SysFeeType::getSortOrder).orderByAsc(SysFeeType::getId);
        for (SysFeeType type : feeTypeMapper.selectList(wrapper)) {
            rows.add(toRow(type));
        }
        rows.sort(Comparator.comparing(FeeTypePageVO.Row::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FeeTypePageVO.Row::getFeeCode));

        int pageNo = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int from = Math.min((pageNo - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());

        FeeTypePageVO vo = new FeeTypePageVO();
        vo.setRows(rows.subList(from, to));
        vo.setTotal(rows.size());
        vo.setPage(pageNo);
        vo.setPageSize(size);
        vo.setCounts(counts());
        return vo;
    }

    /* ---------------- #27 目录 ---------------- */

    public List<FeeTypeCatalogVO> catalog(Integer feeCategory) {
        List<SysFeeType> types = feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                .eq(feeCategory != null, SysFeeType::getFeeCategory, feeCategory)
                .eq(SysFeeType::getEnabled, false)
                .orderByAsc(SysFeeType::getSortOrder));
        List<FeeTypeCatalogVO> list = new ArrayList<>();
        for (SysFeeType type : types) {
            FeeTypeCatalogVO vo = new FeeTypeCatalogVO();
            vo.setFeeCode(type.getFeeCode());
            vo.setFeeName(type.getFeeName());
            vo.setFeeCategory(type.getFeeCategory());
            vo.setDefaultDirection(type.getProfitDirection());
            vo.setConfigured(true);
            list.add(vo);
        }
        return list;
    }

    /* ---------------- #28 新增 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(FeeTypeSaveRequest request) {
        int category = request.getFeeCategory();
        String name = request.getFeeName().trim();
        long duplicate = feeTypeMapper.selectCount(Wrappers.<SysFeeType>lambdaQuery()
                .eq(SysFeeType::getFeeName, name)
                .eq(SysFeeType::getFeeCategory, category));
        if (duplicate > 0) {
            throw new BizException(400, "同类型下费用名称已存在：" + name);
        }
        SysFeeType type = new SysFeeType();
        type.setFeeCode(nextCode(category));
        type.setFeeName(name);
        type.setFeeCategory(category);
        type.setEnabled(request.getEnabled() == null || request.getEnabled());
        if (category == 3 || category == 4) {
            type.setProfitEnabled(false);
            type.setProfitDirection(null);
        } else if (category == 1) {
            type.setProfitEnabled(request.getProfitEnabled() == null || request.getProfitEnabled());
            type.setProfitDirection(1);
        } else {
            type.setProfitEnabled(request.getProfitEnabled() == null || request.getProfitEnabled());
            type.setProfitDirection(2);
        }
        type.setSourceField(null);
        type.setSortOrder(request.getSortOrder() == null ? 999 : request.getSortOrder());
        type.setRemark(request.getRemark());
        type.setDeptId(1L);
        feeTypeMapper.insert(type);
        if (category == 3 || category == 4) {
            // 代收/代付方向必须为空：insert 默认跳过 null，需显式置空
            feeTypeMapper.update(null, new UpdateWrapper<SysFeeType>()
                    .eq("id", type.getId())
                    .set("profit_direction", null));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", type.getId());
        result.put("feeCode", type.getFeeCode());
        return result;
    }

    /* ---------------- #29 编辑 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FeeTypeUpdateRequest request) {
        SysFeeType type = requireType(id);
        UpdateWrapper<SysFeeType> update = new UpdateWrapper<>();
        update.eq("id", id);
        if (request.getEnabled() != null) {
            update.set("enabled", request.getEnabled());
        }
        if (request.getSortOrder() != null) {
            update.set("sort_order", request.getSortOrder());
        }
        if (request.getRemark() != null) {
            update.set("remark", request.getRemark());
        }
        if (Objects.equals(type.getFeeCategory(), 3) || Objects.equals(type.getFeeCategory(), 4)) {
            update.set("profit_enabled", false).set("profit_direction", null);
        } else if (request.getProfitEnabled() != null) {
            update.set("profit_enabled", request.getProfitEnabled());
        }
        feeTypeMapper.update(null, update);
    }

    /* ---------------- #30 启用/停用 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, FeeStatusRequest request) {
        requireType(id);
        feeTypeMapper.update(null, new UpdateWrapper<SysFeeType>()
                .eq("id", id)
                .set("enabled", request.getEnabled()));
    }

    /* ---------------- 内部 ---------------- */

    private SysFeeType requireType(Long id) {
        SysFeeType type = feeTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException(404, "费用类型不存在");
        }
        return type;
    }

    private String nextCode(int category) {
        if (category == 1) {
            int max = 4;
            for (SysFeeType type : feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                    .eq(SysFeeType::getFeeCategory, 1))) {
                String code = type.getFeeCode();
                if (code != null && code.startsWith("INC")) {
                    try {
                        max = Math.max(max, Integer.parseInt(code.substring(3)));
                    } catch (NumberFormatException ignored) {
                        // 忽略非标准编码
                    }
                }
            }
            return String.format("INC%02d", max + 1);
        }
        int max = 25;
        for (SysFeeType type : feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                .likeRight(SysFeeType::getFeeCode, "FEE"))) {
            String code = type.getFeeCode();
            if (code != null && code.length() == 7) {
                try {
                    max = Math.max(max, Integer.parseInt(code.substring(3)));
                } catch (NumberFormatException ignored) {
                    // 忽略非标准编码
                }
            }
        }
        return String.format("FEE%04d", max + 1);
    }

    private FeeTypePageVO.Counts counts() {
        FeeTypePageVO.Counts counts = new FeeTypePageVO.Counts();
        counts.setIncome(INCOME_NAMES.size());
        counts.setCost(0);
        counts.setCollect(0);
        counts.setPayable(0);
        for (SysFeeType type : feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery())) {
            if (Objects.equals(type.getFeeCategory(), 1)) {
                counts.setIncome(counts.getIncome() + 1);
            } else if (Objects.equals(type.getFeeCategory(), 2)) {
                counts.setCost(counts.getCost() + 1);
            } else if (Objects.equals(type.getFeeCategory(), 3)) {
                counts.setCollect(counts.getCollect() + 1);
            } else if (Objects.equals(type.getFeeCategory(), 4)) {
                counts.setPayable(counts.getPayable() + 1);
            }
        }
        return counts;
    }

    private FeeTypePageVO.Row toRow(SysFeeType type) {
        FeeTypePageVO.Row row = new FeeTypePageVO.Row();
        row.setId(type.getId());
        row.setFeeCode(type.getFeeCode());
        row.setFeeName(type.getFeeName());
        row.setFeeCategory(type.getFeeCategory());
        row.setFeeCategoryName(categoryName(type.getFeeCategory()));
        row.setEnabled(type.getEnabled());
        row.setProfitEnabled(type.getProfitEnabled());
        row.setProfitDirection(type.getProfitDirection());
        row.setSourceField(type.getSourceField());
        row.setSortOrder(type.getSortOrder());
        row.setRemark(type.getRemark());
        row.setReadonly(false);
        return row;
    }

    private String categoryName(Integer category) {
        if (category == null) {
            return "";
        }
        return switch (category) {
            case 1 -> "收入";
            case 2 -> "成本";
            case 3 -> "代收";
            case 4 -> "代付";
            default -> "";
        };
    }

    private boolean match(String value, String target) {
        return !StringUtils.hasText(value) || target.contains(value.trim());
    }
}
