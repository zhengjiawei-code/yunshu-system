package com.yunshu.modules.transport.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.transport.dto.GrossFormulaSaveRequest;
import com.yunshu.modules.transport.dto.GrossFormulaTrialRequest;
import com.yunshu.modules.transport.entity.BizTrip;
import com.yunshu.modules.transport.entity.BizTripWaybill;
import com.yunshu.modules.transport.entity.BizTripWaybillFee;
import com.yunshu.modules.transport.entity.SysConfig;
import com.yunshu.modules.transport.entity.SysFeeType;
import com.yunshu.modules.transport.mapper.BizTripMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillFeeMapper;
import com.yunshu.modules.transport.mapper.BizTripWaybillMapper;
import com.yunshu.modules.transport.mapper.SysConfigMapper;
import com.yunshu.modules.transport.mapper.SysFeeTypeMapper;
import com.yunshu.modules.transport.vo.GrossFormulaTrialVO;
import com.yunshu.modules.transport.vo.GrossFormulaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运单毛利公式（#38/#39/#40）：结构化加/减项，收入 4 项 + 成本项，返款可扣。
 * 单票毛利 = Σ加项 − 返款(可选) − Σ减项；车次毛利 = Σ已配载票毛利。
 */
@Service
@RequiredArgsConstructor
public class GrossFormulaService {

    public static final String CONFIG_KEY = "gross.formula";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int HISTORY_LIMIT = 20;

    private static final Map<String, String> INCOME_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> INCOME_FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> INCOME_SOURCE = new LinkedHashMap<>();

    static {
        INCOME_NAMES.put("INC01", "现付运费");
        INCOME_NAMES.put("INC02", "到付运费");
        INCOME_NAMES.put("INC03", "回付运费");
        INCOME_NAMES.put("INC04", "月结运费");
        INCOME_FIELDS.put("INC01", "prepaid_fee");
        INCOME_FIELDS.put("INC02", "collect_fee");
        INCOME_FIELDS.put("INC03", "return_fee");
        INCOME_FIELDS.put("INC04", "monthly_fee");
        INCOME_SOURCE.put("INC01", "biz_waybill.prepaid_fee");
        INCOME_SOURCE.put("INC02", "biz_waybill.collect_fee");
        INCOME_SOURCE.put("INC03", "biz_waybill.return_fee");
        INCOME_SOURCE.put("INC04", "biz_waybill.monthly_fee");
    }

    private final SysConfigMapper configMapper;
    private final SysFeeTypeMapper feeTypeMapper;
    private final BizTripWaybillFeeMapper tripWaybillFeeMapper;
    private final BizTripWaybillMapper tripWaybillMapper;
    private final BizTripMapper tripMapper;
    private final SysUserMapper userMapper;
    private final UserOrgService userOrgService;
    private final ObjectMapper objectMapper;

    /* ---------------- #38 读取 ---------------- */

    public GrossFormulaVO get() {
        GrossFormulaVO formula = current();
        formula.setIncomeCandidates(incomeCandidates());
        formula.setCostCandidates(costCandidates());
        formula.setFormulaText(formulaText(formula));
        return formula;
    }

    public GrossFormulaVO current() {
        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaultFormula();
        }
        try {
            GrossFormulaVO formula = objectMapper.readValue(config.getConfigValue(), GrossFormulaVO.class);
            if (formula.getItems() == null || formula.getItems().isEmpty()) {
                return defaultFormula();
            }
            if (formula.getVersion() == null) {
                formula.setVersion(1);
            }
            if (formula.getRebateDeduct() == null) {
                formula.setRebateDeduct(true);
            }
            return formula;
        } catch (Exception ex) {
            return defaultFormula();
        }
    }

    /* ---------------- #39 保存 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public GrossFormulaVO save(GrossFormulaSaveRequest request) {
        UserOrgService.UserOrg org = userOrgService.current();
        Long operatorId = org.userId();
        String operatorName = operatorName(operatorId);
        GrossFormulaVO current = current();
        if (request.getVersion() != null && !request.getVersion().equals(current.getVersion())) {
            throw new BizException(409, "公式已被他人修改，请刷新后重试");
        }
        Map<String, SysFeeType> costTypes = costTypes();
        List<GrossFormulaVO.Item> items = new ArrayList<>();
        boolean hasIncome = false;
        for (GrossFormulaSaveRequest.Item item : request.getItems()) {
            String code = item.getFeeCode().trim();
            String side = item.getSide() == null ? "" : item.getSide().trim();
            if (!"add".equals(side) && !"sub".equals(side)) {
                throw new BizException(400, "公式项方向不合法：" + code);
            }
            GrossFormulaVO.Item saved = new GrossFormulaVO.Item();
            saved.setFeeCode(code);
            if (INCOME_NAMES.containsKey(code)) {
                if (!"add".equals(side)) {
                    throw new BizException(400, "收入项只能为加项：" + code);
                }
                hasIncome = true;
                saved.setName(INCOME_NAMES.get(code));
            } else {
                SysFeeType type = costTypes.get(code);
                if (type == null) {
                    throw new BizException(400, "费用项不存在或未启用：" + code);
                }
                saved.setName(type.getFeeName());
            }
            saved.setSide(side);
            items.add(saved);
        }
        if (!hasIncome) {
            throw new BizException(400, "至少保留一个收入项");
        }

        int nextVersion = current.getVersion() + 1;
        LocalDateTime now = LocalDateTime.now();
        List<GrossFormulaVO.History> history = new ArrayList<>();
        GrossFormulaVO.History record = new GrossFormulaVO.History();
        record.setVersion(current.getVersion());
        record.setUpdatedBy(operatorName);
        record.setUpdatedAt(now.format(DATE_TIME));
        record.setSummary(StringUtils.hasText(request.getSummary())
                ? request.getSummary().trim() : "公式调整（" + items.size() + " 项）");
        history.add(record);
        if (current.getHistory() != null) {
            history.addAll(current.getHistory());
        }
        if (history.size() > HISTORY_LIMIT) {
            history = new ArrayList<>(history.subList(0, HISTORY_LIMIT));
        }

        GrossFormulaVO saved = new GrossFormulaVO();
        saved.setVersion(nextVersion);
        saved.setRebateDeduct(request.getRebateDeduct() == null || request.getRebateDeduct());
        saved.setItems(items);
        saved.setUpdatedBy(operatorName);
        saved.setUpdatedAt(now.format(DATE_TIME));
        saved.setHistory(history);
        saved.setIncomeCandidates(incomeCandidates());
        saved.setCostCandidates(costCandidates());
        saved.setFormulaText(formulaText(saved));

        String json;
        try {
            json = objectMapper.writeValueAsString(saved);
        } catch (Exception ex) {
            throw new BizException(500, "公式序列化失败");
        }
        SysConfig config = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(CONFIG_KEY);
            config.setConfigValue(json);
            config.setConfigName("运单毛利计算公式");
            config.setValueType("json");
            config.setRemark("单票公式：收入加项 − 返款(rebateDeduct=true时) − 成本减项");
            config.setCreateBy(operatorId);
            config.setUpdateBy(operatorId);
            configMapper.insert(config);
        } else {
            config.setConfigValue(json);
            config.setUpdateBy(operatorId);
            config.setUpdateTime(now);
            configMapper.updateById(config);
        }
        return saved;
    }

    /* ---------------- #40 试算 ---------------- */

    public GrossFormulaTrialVO trial(GrossFormulaTrialRequest request) {
        GrossFormulaVO formula = request.getFormula() == null
                ? current() : draftFormula(request.getFormula());
        BizTripWaybill unit = resolveUnit(request);
        Map<String, BigDecimal> feeAmounts = feeAmounts(unit.getId());

        GrossFormulaTrialVO vo = new GrossFormulaTrialVO();
        vo.setTripWaybillId(unit.getId());
        vo.setWaybillId(unit.getWaybillId());
        vo.setWaybillNo(unit.getWaybillNo());
        vo.setRebate(nvl(unit.getRebateTotal()));
        vo.setRebateDeduct(Boolean.TRUE.equals(formula.getRebateDeduct()));
        vo.setFormulaText(formulaText(formula));

        BigDecimal incomeTotal = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        List<GrossFormulaTrialVO.Line> incomeLines = new ArrayList<>();
        List<GrossFormulaTrialVO.Line> costLines = new ArrayList<>();
        for (GrossFormulaVO.Item item : formula.getItems()) {
            boolean income = INCOME_NAMES.containsKey(item.getFeeCode());
            BigDecimal amount = income ? incomeValue(unit, item.getFeeCode())
                    : feeAmounts.getOrDefault(item.getFeeCode(), BigDecimal.ZERO);
            GrossFormulaTrialVO.Line line = new GrossFormulaTrialVO.Line();
            line.setFeeCode(item.getFeeCode());
            line.setName(item.getName() == null ? item.getFeeCode() : item.getName());
            line.setSide(item.getSide());
            line.setAmount(amount);
            if ("add".equals(item.getSide())) {
                incomeLines.add(line);
                incomeTotal = incomeTotal.add(amount);
            } else {
                costLines.add(line);
                costTotal = costTotal.add(amount);
            }
        }
        vo.setIncomeItems(incomeLines);
        vo.setCostItems(costLines);
        vo.setIncomeTotal(incomeTotal);
        vo.setRebateTotal(vo.getRebateDeduct() ? vo.getRebate() : BigDecimal.ZERO);
        vo.setCostTotal(costTotal);
        vo.setGross(incomeTotal.subtract(vo.getRebateTotal()).subtract(costTotal));

        Long tripId = request.getTripId() == null ? unit.getTripId() : request.getTripId();
        if (tripId != null) {
            BizTrip trip = tripMapper.selectById(tripId);
            if (trip != null) {
                List<BizTripWaybill> rows = tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                        .eq(BizTripWaybill::getTripId, tripId));
                BigDecimal sum = BigDecimal.ZERO;
                for (BizTripWaybill row : rows) {
                    sum = sum.add(nvl(row.getGrossProfit()));
                }
                GrossFormulaTrialVO.TripCheck check = new GrossFormulaTrialVO.TripCheck();
                check.setTripId(tripId);
                check.setTripGross(nvl(trip.getGrossProfit()));
                check.setSumTicketGross(sum);
                check.setDiff(nvl(trip.getGrossProfit()).subtract(sum));
                check.setBalanced(check.getDiff().signum() == 0);
                vo.setTripCheck(check);
            }
        }
        return vo;
    }

    /* ---------------- 供费用重算调用 ---------------- */

    /** 按当前公式计算单票毛利；feeAmounts 为该票 feeCode → 分摊金额 */
    public BigDecimal computeGross(BizTripWaybill unit, Map<String, BigDecimal> feeAmounts,
                                   GrossFormulaVO formula) {
        BigDecimal add = BigDecimal.ZERO;
        BigDecimal sub = BigDecimal.ZERO;
        for (GrossFormulaVO.Item item : formula.getItems()) {
            boolean income = INCOME_NAMES.containsKey(item.getFeeCode());
            BigDecimal amount = income ? incomeValue(unit, item.getFeeCode())
                    : feeAmounts.getOrDefault(item.getFeeCode(), BigDecimal.ZERO);
            if ("add".equals(item.getSide())) {
                add = add.add(amount);
            } else {
                sub = sub.add(amount);
            }
        }
        BigDecimal rebate = Boolean.TRUE.equals(formula.getRebateDeduct()) ? nvl(unit.getRebateTotal()) : BigDecimal.ZERO;
        return add.subtract(rebate).subtract(sub);
    }

    public String formulaTextOf(GrossFormulaVO formula) {
        return formulaText(formula);
    }

    /* ---------------- 内部 ---------------- */

    private GrossFormulaVO draftFormula(GrossFormulaSaveRequest request) {
        GrossFormulaVO draft = new GrossFormulaVO();
        draft.setRebateDeduct(request.getRebateDeduct() == null || request.getRebateDeduct());
        List<GrossFormulaVO.Item> items = new ArrayList<>();
        if (request.getItems() != null) {
            for (GrossFormulaSaveRequest.Item item : request.getItems()) {
                GrossFormulaVO.Item row = new GrossFormulaVO.Item();
                row.setFeeCode(item.getFeeCode());
                row.setName(StringUtils.hasText(item.getName()) ? item.getName()
                        : INCOME_NAMES.getOrDefault(item.getFeeCode(), item.getFeeCode()));
                row.setSide(item.getSide());
                items.add(row);
            }
        }
        draft.setItems(items);
        return draft;
    }

    private BizTripWaybill resolveUnit(GrossFormulaTrialRequest request) {
        if (request.getTripWaybillId() != null) {
            BizTripWaybill unit = tripWaybillMapper.selectById(request.getTripWaybillId());
            if (unit == null) {
                throw new BizException(400, "样例配载票不存在");
            }
            return unit;
        }
        if (request.getWaybillId() == null) {
            throw new BizException(400, "请选择样例运单");
        }
        List<BizTripWaybill> rows = tripWaybillMapper.selectList(Wrappers.<BizTripWaybill>lambdaQuery()
                .eq(BizTripWaybill::getWaybillId, request.getWaybillId())
                .orderByDesc(BizTripWaybill::getId)
                .last("LIMIT 1"));
        if (rows.isEmpty()) {
            throw new BizException(400, "样例运单未配载，无法试算");
        }
        return rows.get(0);
    }

    private Map<String, BigDecimal> feeAmounts(Long tripWaybillId) {
        List<BizTripWaybillFee> rows = tripWaybillFeeMapper.selectList(Wrappers.<BizTripWaybillFee>lambdaQuery()
                .eq(BizTripWaybillFee::getTripWaybillId, tripWaybillId));
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (BizTripWaybillFee row : rows) {
            amounts.merge(row.getFeeCode(), nvl(row.getAmount()), BigDecimal::add);
        }
        return amounts;
    }

    private BigDecimal incomeValue(BizTripWaybill unit, String feeCode) {
        return switch (feeCode) {
            case "INC01" -> nvl(unit.getPrepaidFee());
            case "INC02" -> nvl(unit.getCollectFee());
            case "INC03" -> nvl(unit.getReturnFee());
            case "INC04" -> nvl(unit.getMonthlyFee());
            default -> BigDecimal.ZERO;
        };
    }

    private Map<String, SysFeeType> costTypes() {
        List<SysFeeType> types = feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                .eq(SysFeeType::getEnabled, true)
                .eq(SysFeeType::getFeeCategory, 2)
                .orderByAsc(SysFeeType::getSortOrder));
        Map<String, SysFeeType> map = new LinkedHashMap<>();
        for (SysFeeType type : types) {
            map.put(type.getFeeCode(), type);
        }
        return map;
    }

    private List<GrossFormulaVO.Candidate> incomeCandidates() {
        List<GrossFormulaVO.Candidate> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : INCOME_NAMES.entrySet()) {
            GrossFormulaVO.Candidate candidate = new GrossFormulaVO.Candidate();
            candidate.setFeeCode(entry.getKey());
            candidate.setFeeName(entry.getValue());
            candidate.setSide("add");
            candidate.setSourceField(INCOME_SOURCE.get(entry.getKey()));
            candidate.setReadonly(true);
            list.add(candidate);
        }
        return list;
    }

    private List<GrossFormulaVO.Candidate> costCandidates() {
        List<SysFeeType> types = feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                .eq(SysFeeType::getEnabled, true)
                .eq(SysFeeType::getFeeCategory, 2)
                .eq(SysFeeType::getProfitEnabled, true)
                .orderByAsc(SysFeeType::getSortOrder));
        List<GrossFormulaVO.Candidate> list = new ArrayList<>();
        for (SysFeeType type : types) {
            GrossFormulaVO.Candidate candidate = new GrossFormulaVO.Candidate();
            candidate.setFeeCode(type.getFeeCode());
            candidate.setFeeName(type.getFeeName());
            candidate.setSide("sub");
            candidate.setSourceField(type.getSourceField());
            candidate.setReadonly(false);
            list.add(candidate);
        }
        return list;
    }

    private GrossFormulaVO defaultFormula() {
        GrossFormulaVO formula = new GrossFormulaVO();
        formula.setVersion(1);
        formula.setRebateDeduct(true);
        List<GrossFormulaVO.Item> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : INCOME_NAMES.entrySet()) {
            GrossFormulaVO.Item item = new GrossFormulaVO.Item();
            item.setFeeCode(entry.getKey());
            item.setName(entry.getValue());
            item.setSide("add");
            items.add(item);
        }
        for (SysFeeType type : feeTypeMapper.selectList(Wrappers.<SysFeeType>lambdaQuery()
                .eq(SysFeeType::getEnabled, true)
                .eq(SysFeeType::getFeeCategory, 2)
                .eq(SysFeeType::getProfitEnabled, true)
                .orderByAsc(SysFeeType::getSortOrder))) {
            GrossFormulaVO.Item item = new GrossFormulaVO.Item();
            item.setFeeCode(type.getFeeCode());
            item.setName(type.getFeeName());
            item.setSide("sub");
            items.add(item);
        }
        formula.setItems(items);
        formula.setUpdatedBy("system");
        formula.setHistory(new ArrayList<>());
        return formula;
    }

    private String formulaText(GrossFormulaVO formula) {
        if (formula.getItems() == null || formula.getItems().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (GrossFormulaVO.Item item : formula.getItems()) {
            String name = item.getName() == null ? item.getFeeCode() : item.getName();
            if (builder.length() > 0) {
                builder.append("sub".equals(item.getSide()) ? " − " : " + ");
            } else if ("sub".equals(item.getSide())) {
                builder.append("− ");
            }
            builder.append(name);
        }
        if (Boolean.TRUE.equals(formula.getRebateDeduct())) {
            builder.append(builder.length() > 0 ? " − " : "").append("返款合计");
        }
        return builder.toString();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String operatorName(Long userId) {
        if (userId == null) {
            return "system";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getRealName()) ? "system" : user.getRealName();
    }
}
