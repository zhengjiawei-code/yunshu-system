package com.yunshu.modules.operation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.constant.WaybillEnums;
import com.yunshu.modules.operation.dto.WaybillAmendRequest;
import com.yunshu.modules.operation.dto.WaybillSaveRequest;
import com.yunshu.modules.operation.dto.WaybillVoidRequest;
import com.yunshu.modules.operation.entity.BizCustomer;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.entity.BizWaybillGoods;
import com.yunshu.modules.operation.entity.BizWaybillLog;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import com.yunshu.modules.operation.mapper.BizCustomerMapper;
import com.yunshu.modules.operation.mapper.BizWaybillGoodsMapper;
import com.yunshu.modules.operation.mapper.BizWaybillLogMapper;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.operation.mapper.BizWaybillRouteNodeMapper;
import com.yunshu.modules.operation.vo.CustomerMatchVO;
import com.yunshu.modules.operation.vo.RouteCandidateVO;
import com.yunshu.modules.operation.vo.WaybillAmendLogVO;
import com.yunshu.modules.operation.vo.WaybillAmendVO;
import com.yunshu.modules.operation.vo.WaybillDetailVO;
import com.yunshu.modules.operation.vo.WaybillFieldChangeVO;
import com.yunshu.modules.operation.vo.WaybillSaveVO;
import com.yunshu.modules.operation.vo.WaybillVoidVO;
import com.yunshu.modules.system.dept.entity.SysDept;
import com.yunshu.modules.system.dept.mapper.SysDeptMapper;
import com.yunshu.modules.system.dept.service.UserOrgService;
import com.yunshu.modules.system.region.entity.SysRegion;
import com.yunshu.modules.system.region.mapper.SysRegionMapper;
import com.yunshu.modules.system.user.entity.SysUser;
import com.yunshu.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 开单保存（方案 3.1 / 5.4 / 6.7 / 8.2）。
 * 必填校验、金额派生、客户匹配、路由二次匹配、网点派生全部在服务端完成；
 * 客户端传入的客户、网点、路由名称、派生金额一律忽略。
 */
@Service
@RequiredArgsConstructor
public class WaybillService {

    private final BizWaybillMapper waybillMapper;
    private final BizWaybillRouteNodeMapper nodeMapper;
    private final BizWaybillGoodsMapper goodsMapper;
    private final BizWaybillLogMapper logMapper;
    private final BizCustomerMapper customerMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysRegionMapper regionMapper;
    private final UserOrgService userOrgService;
    private final CustomerMatchService customerMatchService;
    private final RouteCandidateService routeCandidateService;
    private final WaybillAmountCalculator amountCalculator;
    private final WaybillNoGenerator noGenerator;
    private final ObjectMapper objectMapper;

    /** 改单可写白名单：payload 里只有这些键会被接受 */
    private static final Set<String> AMENDABLE_FIELDS = Set.of(
            "senderCompany", "senderName", "senderPhone", "senderAddress",
            "receiverCompany", "receiverName", "receiverPhone",
            "receiverProvinceCode", "receiverCityCode", "receiverDistrictCode", "receiverAddress",
            "goodsList", "payMethod",
            "freightFee", "deliveryFee", "pickupFee", "otherFee", "receiptFee", "makeBillFee",
            "insuranceFee", "cashBack", "oweBack",
            "prepaidFee", "collectFee", "monthlyFee", "returnFee",
            "deliveryMethod", "codAmount", "payeeName", "bankName", "bankCardNo", "remark");
    /** 参与运费合计的输入项；出现任意一项即重算派生金额 */
    private static final Set<String> FEE_FIELDS = Set.of(
            "freightFee", "deliveryFee", "pickupFee", "otherFee", "receiptFee", "makeBillFee",
            "insuranceFee", "cashBack", "oweBack");
    private static final Set<String> REGION_FIELDS = Set.of(
            "receiverProvinceCode", "receiverCityCode", "receiverDistrictCode");

    @Transactional(rollbackFor = Exception.class)
    public WaybillSaveVO create(WaybillSaveRequest request) {
        DataScopeContext context = DataScopeContext.require("开单保存");
        validate(request);
        Long originDeptId = resolveOriginDept(request.getOriginDeptId(), context);
        SysDept originDept = deptMapper.selectById(originDeptId);

        RouteCandidateVO route = requireSelectedRoute(request, originDeptId);
        validateRouteService(route, request.getDeliveryMethod());
        WaybillAmountCalculator.AmountResult amounts = amountCalculator.calculate(request);
        BizCustomer customer = resolveCustomer(request.getSenderName(), request.getSenderPhone());

        GoodsSummary goods = summarize(request.getGoodsList());
        assertMainTotalsMatch(request, goods);
        ReceiverRegion receiverRegion = resolveReceiverRegion(request);
        SysUser operator = context.getUserId() == null ? null : userMapper.selectById(context.getUserId());
        String operatorName = operator == null ? "系统" : operator.getRealName();

        BizWaybill waybill = new BizWaybill();
        waybill.setWaybillNo(noGenerator.nextWaybillNo());
        waybill.setRelatedNo(trimToNull(request.getRelatedNo()));
        waybill.setIsChild(0);
        waybill.setStatus(20);
        waybill.setDeptId(originDeptId);
        waybill.setDeptName(originDept.getDeptName());
        waybill.setSalesId(request.getSalesId());
        if (request.getSalesId() != null) {
            SysUser sales = userMapper.selectById(request.getSalesId());
            waybill.setSalesName(sales == null ? null : sales.getRealName());
        } else {
            waybill.setSalesName(trimToNull(request.getSalesName()));
        }
        waybill.setBizType(request.getBizType() == null ? 2 : request.getBizType());

        waybill.setDestProvinceCode(route.getProvinceCode());
        waybill.setDestProvinceName(route.getProvinceName());
        waybill.setDestCityCode(route.getCityCode());
        waybill.setDestCityName(route.getCityName());
        waybill.setDestDistrictCode(route.getDistrictCode());
        waybill.setDestDistrictName(route.getDistrictName());
        waybill.setDestName(route.getDestName());

        RouteCandidateVO.Node firstNode = route.getNodes().get(0);
        waybill.setOriginHubId(firstNode.getFromDeptId());
        waybill.setOriginHubName(firstNode.getFromDeptName());
        waybill.setDestHubId(route.getDestHubId());
        waybill.setDestHubName(route.getDestHubName());
        waybill.setRouteName(route.getChainText());
        waybill.setRouteNodeCount(route.getNodeCount());
        waybill.setRouteEndType(route.getEndType());

        waybill.setCustomerId(customer == null ? null : customer.getId());
        waybill.setCustomerName(customer == null ? "" : customer.getCustomerName());
        waybill.setCustomerPhone(customer == null ? null : customer.getContactPhone());

        waybill.setSenderName(request.getSenderName().trim());
        waybill.setSenderPhone(request.getSenderPhone().trim());
        waybill.setSenderCompany(trimToNull(request.getSenderCompany()));
        waybill.setSenderAddress(request.getSenderAddress().trim());
        waybill.setReceiverName(request.getReceiverName().trim());
        waybill.setReceiverPhone(request.getReceiverPhone().trim());
        waybill.setReceiverCompany(trimToNull(request.getReceiverCompany()));
        waybill.setReceiverProvinceCode(receiverRegion.provinceCode());
        waybill.setReceiverProvinceName(receiverRegion.provinceName());
        waybill.setReceiverCityCode(receiverRegion.cityCode());
        waybill.setReceiverCityName(receiverRegion.cityName());
        waybill.setReceiverDistrictCode(receiverRegion.districtCode());
        waybill.setReceiverDistrictName(receiverRegion.districtName());
        waybill.setReceiverAddress(request.getReceiverAddress().trim());

        waybill.setGoodsName(goods.name());
        waybill.setGoodsType(goods.type());
        waybill.setPackageType(goods.packageType());
        waybill.setQuantity(goods.quantity());
        waybill.setWeight(goods.weight());
        waybill.setVolume(goods.volume());
        waybill.setGoodsRemark(goods.remark());

        waybill.setPayMethod(request.getPayMethod());
        waybill.setFreightFee(money(request.getFreightFee()));
        waybill.setDeliveryFee(money(request.getDeliveryFee()));
        waybill.setPickupFee(money(request.getPickupFee()));
        waybill.setOtherFee(money(request.getOtherFee()));
        waybill.setReceiptFee(money(request.getReceiptFee()));
        waybill.setMakeBillFee(money(request.getMakeBillFee()));
        waybill.setInsuranceFee(money(request.getInsuranceFee()));
        waybill.setDeclaredValue(money(request.getDeclaredValue()));
        waybill.setTotalFee(amounts.totalFee());
        waybill.setPrepaidFee(amounts.prepaidFee());
        waybill.setCollectFee(amounts.collectFee());
        waybill.setMonthlyFee(amounts.monthlyFee());
        waybill.setReturnFee(amounts.returnFee());
        waybill.setCashBack(money(request.getCashBack()));
        waybill.setOweBack(money(request.getOweBack()));
        waybill.setRebateTotal(amounts.rebateTotal());
        waybill.setValidFreight(amounts.validFreight());

        waybill.setDeliveryMethod(request.getDeliveryMethod());
        waybill.setCodAmount(money(request.getCodAmount()));
        waybill.setPayeeName(trimToNull(request.getPayeeName()));
        waybill.setBankName(trimToNull(request.getBankName()));
        waybill.setBankCardNo(trimToNull(request.getBankCardNo()));
        waybill.setNeedReceipt(request.getNeedReceipt() == null ? 0 : request.getNeedReceipt());
        waybill.setReceiptCount(request.getNeedReceipt() != null && request.getNeedReceipt() == 1
                ? request.getReceiptCount() : 0);
        waybill.setWaitNotice(request.getWaitNotice() == null ? 0 : request.getWaitNotice());
        waybill.setRemark(trimToNull(request.getRemark()));
        waybill.setCreateByName(operatorName);
        waybill.setCreateTime(LocalDateTime.now());

        waybillMapper.insert(waybill);
        insertNodes(waybill.getId(), route, originDeptId);
        insertGoods(waybill.getId(), originDeptId, request.getGoodsList());

        return toSaveVO(waybill);
    }

    /** 详情：数据权限由拦截器追加 dept_id 条件，越权等同不存在；日志统一挂母单 */
    public WaybillDetailVO detail(Long id) {
        BizWaybill waybill = id == null ? null : waybillMapper.selectById(id);
        if (waybill == null) {
            throw new BizException(403, "无权访问该运单");
        }
        List<BizWaybillRouteNode> nodes = nodeMapper.selectList(Wrappers.<BizWaybillRouteNode>lambdaQuery()
                .eq(BizWaybillRouteNode::getWaybillId, id)
                .orderByAsc(BizWaybillRouteNode::getSeq));
        List<BizWaybillGoods> goods = goodsMapper.selectList(Wrappers.<BizWaybillGoods>lambdaQuery()
                .eq(BizWaybillGoods::getWaybillId, id)
                .orderByAsc(BizWaybillGoods::getSeq));
        Long logWaybillId = isChild(waybill) && waybill.getParentId() != null
                ? waybill.getParentId() : waybill.getId();
        List<BizWaybillLog> logs = logMapper.selectList(Wrappers.<BizWaybillLog>lambdaQuery()
                .eq(BizWaybillLog::getWaybillId, logWaybillId)
                .orderByDesc(BizWaybillLog::getCreateTime)
                .orderByDesc(BizWaybillLog::getId));
        List<BizWaybillLog> amendLogs = logs.stream()
                .filter(log -> Integer.valueOf(WaybillEnums.LogAction.AMEND.getCode()).equals(log.getActionType()))
                .toList();
        List<WaybillAmendLogVO> amendRecords = amendLogs.stream().map(this::toAmendLogVO).toList();
        int childCount = isChild(waybill) ? 0 : Math.toIntExact(waybillMapper.selectCount(
                Wrappers.<BizWaybill>lambdaQuery().eq(BizWaybill::getParentId, waybill.getId())));

        WaybillDetailVO vo = new WaybillDetailVO();
        vo.setWaybill(waybill);
        vo.setNodes(nodes);
        vo.setGoods(goods);
        vo.setLogs(logs);
        vo.setAmendLogs(amendRecords);
        vo.setChildCount(childCount);
        return vo;
    }

    /** 改单日志转展示对象：changes 从 after_snapshot 一次解析，旧记录无快照时返回空明细 */
    private WaybillAmendLogVO toAmendLogVO(BizWaybillLog log) {
        List<WaybillFieldChangeVO> changes = new ArrayList<>();
        if (StringUtils.hasText(log.getAfterSnapshot())) {
            try {
                JsonNode node = objectMapper.readTree(log.getAfterSnapshot()).path("changes");
                if (node.isArray()) {
                    changes = objectMapper.convertValue(node, new TypeReference<List<WaybillFieldChangeVO>>() {
                    });
                }
            } catch (Exception ignored) {
                // 历史快照格式异常不阻断详情，按无明细展示
            }
        }
        WaybillAmendLogVO vo = new WaybillAmendLogVO();
        vo.setId(log.getId());
        vo.setOperatorName(log.getOperatorName());
        vo.setCreateTime(log.getCreateTime());
        vo.setReason(log.getReason());
        vo.setChanges(changes);
        vo.setChangeCount(changes.size());
        return vo;
    }

    /**
     * 作废（方案 6.1 / D5）：一期任意状态可作废，900 拒绝重复作废；
     * 原因必填 ≤500 字；写 action_type=4 日志，before_snapshot.waybill.status 即作废时状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaybillVoidVO voidWaybill(Long id, WaybillVoidRequest request) {
        DataScopeContext context = DataScopeContext.require("作废运单");
        if (request == null) {
            throw new BizException("作废原因不能为空");
        }
        String reason = trimToNull(request.getReason());
        if (reason == null) {
            throw new BizException("作废原因不能为空");
        }
        if (reason.length() > 500) {
            throw new BizException("作废原因不能超过 500 字");
        }
        String version = normalizeVersion(request.getVersion());
        BizWaybill waybill = selectVisible(id);
        if (WaybillEnums.Status.isVoided(waybill.getStatus())) {
            throw new BizException("该运单已作废");
        }
        Map<String, Object> before = snapshot(waybill);

        UpdateWrapper<BizWaybill> update = Wrappers.<BizWaybill>update();
        update.eq("id", waybill.getId())
                .eq("update_time", version)
                .set("status", WaybillEnums.Status.VOIDED.getCode())
                .set("update_by", requireUserId(context));
        int rows = waybillMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(409, "运单已被他人修改，请刷新后重试");
        }
        BizWaybill updated = waybillMapper.selectById(waybill.getId());
        writeLog(waybill, WaybillEnums.LogAction.VOID, reason, before, snapshot(updated), context);

        WaybillVoidVO vo = new WaybillVoidVO();
        vo.setId(updated.getId());
        vo.setStatus(updated.getStatus());
        vo.setUpdateTime(updated.getUpdateTime());
        return vo;
    }

    /**
     * 改单（方案第 8 节 / D4 / D12 / D13）：patch 语义。
     * present 为请求体里实际出现的字段名；只有出现的字段才校验、才进 UPDATE 与 changes，
     * 未出现的字段保持原值，禁止全量覆盖。金额派生仅在本单涉及金额输入时重算。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaybillAmendVO amend(Long id, WaybillAmendRequest request, Set<String> present) {
        DataScopeContext context = DataScopeContext.require("运单改单");
        if (request == null || present == null || present.isEmpty()) {
            throw new BizException("改单内容不能为空");
        }
        BizWaybill waybill = selectVisible(id);
        if (!WaybillEnums.Status.amendable(waybill.getStatus())) {
            throw new BizException("当前状态不可改单：" + statusText(waybill.getStatus()));
        }
        String reason = trimToNull(request.getReason());
        if (reason == null) {
            throw new BizException("改单原因不能为空");
        }
        if (reason.length() > 200) {
            throw new BizException("改单原因不能超过 200 字");
        }
        String version = normalizeVersion(request.getVersion());

        Set<String> fields = new LinkedHashSet<>(present);
        fields.retainAll(AMENDABLE_FIELDS);
        if (fields.isEmpty()) {
            throw new BizException("未检测到任何修改");
        }
        List<BizWaybillGoods> oldGoods = fields.contains("goodsList")
                ? goodsMapper.selectList(Wrappers.<BizWaybillGoods>lambdaQuery()
                        .eq(BizWaybillGoods::getWaybillId, waybill.getId())
                        .orderByAsc(BizWaybillGoods::getSeq))
                : new ArrayList<>();
        // 与库值相同的字段视为未改：既不校验，也不进 SET/changes
        pruneNoopFields(waybill, request, fields, oldGoods);
        if (fields.isEmpty()) {
            throw new BizException("未检测到任何修改");
        }
        validateAmend(request, fields);

        ReceiverRegion region = fields.stream().anyMatch(REGION_FIELDS::contains)
                ? resolveReceiverRegion(request) : null;
        List<WaybillSaveRequest.GoodsItem> goodsItems = fields.contains("goodsList")
                ? (request.getGoodsList() == null ? new ArrayList<>() : request.getGoodsList()) : null;
        GoodsSummary goods = goodsItems == null ? null : summarize(goodsItems);
        boolean recalc = fields.stream().anyMatch(FEE_FIELDS::contains) || fields.contains("payMethod");
        WaybillAmountCalculator.AmountResult amounts = recalc ? calculatePatch(waybill, request, fields) : null;
        // 派生金额逐列与库值比较，值相同的列不写
        Set<String> amountDirty = amounts == null ? new LinkedHashSet<>() : diffAmounts(waybill, amounts);

        List<WaybillFieldChangeVO> changes = diffAmend(waybill, oldGoods, request, fields, region, amounts,
                amountDirty, goodsItems);
        if (changes.isEmpty()) {
            throw new BizException("未检测到任何修改");
        }
        Map<String, Object> before = snapshot(waybill);

        UpdateWrapper<BizWaybill> update = Wrappers.<BizWaybill>update();
        update.eq("id", waybill.getId()).eq("update_time", version);
        applyAmendSets(update, request, fields);
        applyRegionSets(update, waybill, region);
        applyGoodsSets(update, waybill, goods);
        applyAmountSets(update, amounts, amountDirty);
        update.set("update_by", requireUserId(context));
        int rows = waybillMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(409, "运单已被他人修改，请刷新后重试");
        }
        if (goodsItems != null) {
            replaceGoods(waybill.getId(), waybill.getDeptId(), goodsItems, oldGoods);
        }
        BizWaybill updated = waybillMapper.selectById(waybill.getId());
        Map<String, Object> after = snapshot(updated);
        after.put("changes", changes);
        writeLog(waybill, WaybillEnums.LogAction.AMEND, reason, before, after, context);

        WaybillAmendVO vo = new WaybillAmendVO();
        vo.setWaybill(updated);
        vo.setChanges(changes);
        return vo;
    }

    /**
     * 剔除「提交了但与库值相同」的字段：前端 dirty 之外再兜一层，
     * 保证 SET 集合与 changes 集合严格一致，不会出现 0.00 → 0.00 的写入。
     */
    private void pruneNoopFields(BizWaybill w, WaybillAmendRequest r, Set<String> f,
                                 List<BizWaybillGoods> oldGoods) {
        pruneText(f, "senderCompany", r.getSenderCompany(), w.getSenderCompany());
        pruneText(f, "senderName", r.getSenderName(), w.getSenderName());
        pruneText(f, "senderPhone", r.getSenderPhone(), w.getSenderPhone());
        pruneText(f, "senderAddress", r.getSenderAddress(), w.getSenderAddress());
        pruneText(f, "receiverCompany", r.getReceiverCompany(), w.getReceiverCompany());
        pruneText(f, "receiverName", r.getReceiverName(), w.getReceiverName());
        pruneText(f, "receiverPhone", r.getReceiverPhone(), w.getReceiverPhone());
        pruneText(f, "receiverAddress", r.getReceiverAddress(), w.getReceiverAddress());
        pruneText(f, "payeeName", r.getPayeeName(), w.getPayeeName());
        pruneText(f, "bankName", r.getBankName(), w.getBankName());
        pruneText(f, "bankCardNo", r.getBankCardNo(), w.getBankCardNo());
        pruneText(f, "remark", r.getRemark(), w.getRemark());

        if (f.contains("receiverProvinceCode") || f.contains("receiverCityCode") || f.contains("receiverDistrictCode")) {
            if (sameText(r.getReceiverProvinceCode(), w.getReceiverProvinceCode())
                    && sameText(r.getReceiverCityCode(), w.getReceiverCityCode())
                    && sameText(r.getReceiverDistrictCode(), w.getReceiverDistrictCode())) {
                f.removeAll(REGION_FIELDS);
            }
        }

        pruneMoney(f, "freightFee", r.getFreightFee(), w.getFreightFee());
        pruneMoney(f, "deliveryFee", r.getDeliveryFee(), w.getDeliveryFee());
        pruneMoney(f, "pickupFee", r.getPickupFee(), w.getPickupFee());
        pruneMoney(f, "otherFee", r.getOtherFee(), w.getOtherFee());
        pruneMoney(f, "receiptFee", r.getReceiptFee(), w.getReceiptFee());
        pruneMoney(f, "makeBillFee", r.getMakeBillFee(), w.getMakeBillFee());
        pruneMoney(f, "insuranceFee", r.getInsuranceFee(), w.getInsuranceFee());
        pruneMoney(f, "cashBack", r.getCashBack(), w.getCashBack());
        pruneMoney(f, "oweBack", r.getOweBack(), w.getOweBack());
        pruneMoney(f, "codAmount", r.getCodAmount(), w.getCodAmount());
        pruneMoney(f, "prepaidFee", r.getPrepaidFee(), w.getPrepaidFee());
        pruneMoney(f, "collectFee", r.getCollectFee(), w.getCollectFee());
        pruneMoney(f, "monthlyFee", r.getMonthlyFee(), w.getMonthlyFee());
        pruneMoney(f, "returnFee", r.getReturnFee(), w.getReturnFee());

        if (f.contains("payMethod") && java.util.Objects.equals(r.getPayMethod(), w.getPayMethod())) {
            f.remove("payMethod");
        }
        if (f.contains("deliveryMethod") && java.util.Objects.equals(r.getDeliveryMethod(), w.getDeliveryMethod())) {
            f.remove("deliveryMethod");
        }
        if (f.contains("goodsList") && sameGoods(oldGoods, r.getGoodsList())) {
            f.remove("goodsList");
        }
    }

    private void pruneText(Set<String> fields, String key, String payload, String db) {
        if (fields.contains(key) && sameText(payload, db)) {
            fields.remove(key);
        }
    }

    private void pruneMoney(Set<String> fields, String key, BigDecimal payload, BigDecimal db) {
        if (fields.contains(key) && moneyDiffers(payload, db)) {
            return;
        }
        fields.remove(key);
    }

    private boolean sameText(String a, String b) {
        return java.util.Objects.equals(trimToNull(a), trimToNull(b));
    }

    private boolean moneyDiffers(BigDecimal a, BigDecimal b) {
        return nzDecimal(a).compareTo(nzDecimal(b)) != 0;
    }

    private boolean sameGoods(List<BizWaybillGoods> oldRows, List<WaybillSaveRequest.GoodsItem> items) {
        if (items == null) {
            return true;
        }
        if (oldRows.size() != items.size()) {
            return false;
        }
        for (int i = 0; i < oldRows.size(); i++) {
            BizWaybillGoods old = oldRows.get(i);
            WaybillSaveRequest.GoodsItem item = items.get(i);
            if (item == null) {
                return false;
            }
            if (!sameText(old.getGoodsName(), item.getGoodsName())
                    || !sameText(old.getGoodsType(), item.getGoodsType())
                    || !sameText(old.getPackageType(), item.getPackageType())
                    || !sameText(old.getGoodsRemark(), item.getGoodsRemark())) {
                return false;
            }
            if (nzInt(old.getQuantity()) != nzInt(item.getQuantity())
                    || moneyDiffers(old.getWeight(), item.getWeight())
                    || moneyDiffers(old.getVolume(), item.getVolume())) {
                return false;
            }
        }
        return true;
    }

    /** 只把与库值不同的派生金额列交给 SET */
    private Set<String> diffAmounts(BizWaybill w, WaybillAmountCalculator.AmountResult a) {
        Set<String> dirty = new LinkedHashSet<>();
        if (moneyDiffers(w.getTotalFee(), a.totalFee())) {
            dirty.add("totalFee");
        }
        if (moneyDiffers(w.getRebateTotal(), a.rebateTotal())) {
            dirty.add("rebateTotal");
        }
        if (moneyDiffers(w.getValidFreight(), a.validFreight())) {
            dirty.add("validFreight");
        }
        if (moneyDiffers(w.getPrepaidFee(), a.prepaidFee())) {
            dirty.add("prepaidFee");
        }
        if (moneyDiffers(w.getCollectFee(), a.collectFee())) {
            dirty.add("collectFee");
        }
        if (moneyDiffers(w.getMonthlyFee(), a.monthlyFee())) {
            dirty.add("monthlyFee");
        }
        if (moneyDiffers(w.getReturnFee(), a.returnFee())) {
            dirty.add("returnFee");
        }
        return dirty;
    }

    private void applyAmountSets(UpdateWrapper<BizWaybill> update, WaybillAmountCalculator.AmountResult a,
                                 Set<String> dirty) {
        if (a == null || dirty.isEmpty()) {
            return;
        }
        if (dirty.contains("totalFee")) {
            update.set("total_fee", a.totalFee());
        }
        if (dirty.contains("rebateTotal")) {
            update.set("rebate_total", a.rebateTotal());
        }
        if (dirty.contains("validFreight")) {
            update.set("valid_freight", a.validFreight());
        }
        if (dirty.contains("prepaidFee")) {
            update.set("prepaid_fee", a.prepaidFee());
        }
        if (dirty.contains("collectFee")) {
            update.set("collect_fee", a.collectFee());
        }
        if (dirty.contains("monthlyFee")) {
            update.set("monthly_fee", a.monthlyFee());
        }
        if (dirty.contains("returnFee")) {
            update.set("return_fee", a.returnFee());
        }
    }

    /** 收货地区逐列比较，只写变化的列 */
    private void applyRegionSets(UpdateWrapper<BizWaybill> update, BizWaybill w, ReceiverRegion region) {
        if (region == null) {
            return;
        }
        if (!sameText(region.provinceCode(), w.getReceiverProvinceCode())) {
            update.set("receiver_province_code", region.provinceCode());
        }
        if (!sameText(region.provinceName(), w.getReceiverProvinceName())) {
            update.set("receiver_province_name", region.provinceName());
        }
        if (!sameText(region.cityCode(), w.getReceiverCityCode())) {
            update.set("receiver_city_code", region.cityCode());
        }
        if (!sameText(region.cityName(), w.getReceiverCityName())) {
            update.set("receiver_city_name", region.cityName());
        }
        if (!sameText(region.districtCode(), w.getReceiverDistrictCode())) {
            update.set("receiver_district_code", region.districtCode());
        }
        if (!sameText(region.districtName(), w.getReceiverDistrictName())) {
            update.set("receiver_district_name", region.districtName());
        }
    }

    /** 货物汇总逐列比较，只写变化的列 */
    private void applyGoodsSets(UpdateWrapper<BizWaybill> update, BizWaybill w, GoodsSummary goods) {
        if (goods == null) {
            return;
        }
        if (!sameText(goods.name(), w.getGoodsName())) {
            update.set("goods_name", goods.name());
        }
        if (!sameText(goods.type(), w.getGoodsType())) {
            update.set("goods_type", goods.type());
        }
        if (!sameText(goods.packageType(), w.getPackageType())) {
            update.set("package_type", goods.packageType());
        }
        if (nzInt(w.getQuantity()) != nzInt(goods.quantity())) {
            update.set("quantity", goods.quantity());
        }
        if (moneyDiffers(w.getWeight(), goods.weight())) {
            update.set("weight", goods.weight());
        }
        if (moneyDiffers(w.getVolume(), goods.volume())) {
            update.set("volume", goods.volume());
        }
        if (!sameText(goods.remark(), w.getGoodsRemark())) {
            update.set("goods_remark", goods.remark());
        }
    }

    /** 收货地区 diff：名称变化按中文标签记一条；仅编码变化（重名区县）才补记编码 */
    private void diffRegion(List<WaybillFieldChangeVO> changes, BizWaybill w, ReceiverRegion region) {
        boolean provinceNameChanged = !sameText(region.provinceName(), w.getReceiverProvinceName());
        if (!sameText(region.provinceCode(), w.getReceiverProvinceCode()) && !provinceNameChanged) {
            addChange(changes, "receiverProvinceCode", "收货省编码", w.getReceiverProvinceCode(), region.provinceCode());
        } else {
            addChange(changes, "receiverProvinceName", "收货省", w.getReceiverProvinceName(), region.provinceName());
        }
        boolean cityNameChanged = !sameText(region.cityName(), w.getReceiverCityName());
        if (!sameText(region.cityCode(), w.getReceiverCityCode()) && !cityNameChanged) {
            addChange(changes, "receiverCityCode", "收货市编码", w.getReceiverCityCode(), region.cityCode());
        } else {
            addChange(changes, "receiverCityName", "收货市", w.getReceiverCityName(), region.cityName());
        }
        boolean districtNameChanged = !sameText(region.districtName(), w.getReceiverDistrictName());
        if (!sameText(region.districtCode(), w.getReceiverDistrictCode()) && !districtNameChanged) {
            addChange(changes, "receiverDistrictCode", "收货区县编码", w.getReceiverDistrictCode(), region.districtCode());
        } else {
            addChange(changes, "receiverDistrictName", "收货区县", w.getReceiverDistrictName(), region.districtName());
        }
    }

    /** 只把 payload 中出现的标量字段写入 SET；清空即写 NULL */
    private void applyAmendSets(UpdateWrapper<BizWaybill> update, WaybillAmendRequest request, Set<String> fields) {
        if (fields.contains("senderCompany")) {
            update.set("sender_company", trimToNull(request.getSenderCompany()));
        }
        if (fields.contains("senderName")) {
            update.set("sender_name", trimToNull(request.getSenderName()));
        }
        if (fields.contains("senderPhone")) {
            update.set("sender_phone", trimToNull(request.getSenderPhone()));
        }
        if (fields.contains("senderAddress")) {
            update.set("sender_address", trimToNull(request.getSenderAddress()));
        }
        if (fields.contains("receiverCompany")) {
            update.set("receiver_company", trimToNull(request.getReceiverCompany()));
        }
        if (fields.contains("receiverName")) {
            update.set("receiver_name", trimToNull(request.getReceiverName()));
        }
        if (fields.contains("receiverPhone")) {
            update.set("receiver_phone", trimToNull(request.getReceiverPhone()));
        }
        if (fields.contains("receiverAddress")) {
            update.set("receiver_address", trimToNull(request.getReceiverAddress()));
        }
        if (fields.contains("payMethod")) {
            update.set("pay_method", request.getPayMethod());
        }
        if (fields.contains("deliveryMethod")) {
            update.set("delivery_method", request.getDeliveryMethod());
        }
        if (fields.contains("freightFee")) {
            update.set("freight_fee", money(request.getFreightFee()));
        }
        if (fields.contains("deliveryFee")) {
            update.set("delivery_fee", money(request.getDeliveryFee()));
        }
        if (fields.contains("pickupFee")) {
            update.set("pickup_fee", money(request.getPickupFee()));
        }
        if (fields.contains("otherFee")) {
            update.set("other_fee", money(request.getOtherFee()));
        }
        if (fields.contains("receiptFee")) {
            update.set("receipt_fee", money(request.getReceiptFee()));
        }
        if (fields.contains("makeBillFee")) {
            update.set("make_bill_fee", money(request.getMakeBillFee()));
        }
        if (fields.contains("insuranceFee")) {
            update.set("insurance_fee", money(request.getInsuranceFee()));
        }
        if (fields.contains("cashBack")) {
            update.set("cash_back", money(request.getCashBack()));
        }
        if (fields.contains("oweBack")) {
            update.set("owe_back", money(request.getOweBack()));
        }
        if (fields.contains("codAmount")) {
            update.set("cod_amount", money(request.getCodAmount()));
        }
        if (fields.contains("payeeName")) {
            update.set("payee_name", trimToNull(request.getPayeeName()));
        }
        if (fields.contains("bankName")) {
            update.set("bank_name", trimToNull(request.getBankName()));
        }
        if (fields.contains("bankCardNo")) {
            update.set("bank_card_no", trimToNull(request.getBankCardNo()));
        }
        if (fields.contains("remark")) {
            update.set("remark", trimToNull(request.getRemark()));
        }
    }

    /** 金额派生：以「payload 优先、未出现取库中原值」拼出有效入参后交给计算器 */
    private WaybillAmountCalculator.AmountResult calculatePatch(BizWaybill waybill, WaybillAmendRequest request,
                                                                Set<String> fields) {
        WaybillSaveRequest effective = new WaybillSaveRequest();
        effective.setFreightFee(fields.contains("freightFee") ? request.getFreightFee() : waybill.getFreightFee());
        effective.setDeliveryFee(fields.contains("deliveryFee") ? request.getDeliveryFee() : waybill.getDeliveryFee());
        effective.setPickupFee(fields.contains("pickupFee") ? request.getPickupFee() : waybill.getPickupFee());
        effective.setOtherFee(fields.contains("otherFee") ? request.getOtherFee() : waybill.getOtherFee());
        effective.setReceiptFee(fields.contains("receiptFee") ? request.getReceiptFee() : waybill.getReceiptFee());
        effective.setMakeBillFee(fields.contains("makeBillFee") ? request.getMakeBillFee() : waybill.getMakeBillFee());
        effective.setInsuranceFee(fields.contains("insuranceFee") ? request.getInsuranceFee() : waybill.getInsuranceFee());
        effective.setCashBack(fields.contains("cashBack") ? request.getCashBack() : waybill.getCashBack());
        effective.setOweBack(fields.contains("oweBack") ? request.getOweBack() : waybill.getOweBack());
        effective.setDeclaredValue(waybill.getDeclaredValue());
        effective.setPayMethod(fields.contains("payMethod") ? request.getPayMethod() : waybill.getPayMethod());
        effective.setPrepaidFee(fields.contains("prepaidFee") ? request.getPrepaidFee() : waybill.getPrepaidFee());
        effective.setCollectFee(fields.contains("collectFee") ? request.getCollectFee() : waybill.getCollectFee());
        effective.setMonthlyFee(fields.contains("monthlyFee") ? request.getMonthlyFee() : waybill.getMonthlyFee());
        effective.setReturnFee(fields.contains("returnFee") ? request.getReturnFee() : waybill.getReturnFee());
        return amountCalculator.calculate(effective);
    }

    /** 可选开单网点：管理范围 + 启用 + 分拨中心/网点 + 允许发货 */
    public List<com.yunshu.modules.operation.vo.DeptOptionVO> openDepts() {
        Set<Long> allowed = userOrgService.manageDeptIds();
        List<com.yunshu.modules.operation.vo.DeptOptionVO> result = new ArrayList<>();
        for (SysDept dept : userOrgService.allDepts()) {
            if (!allowed.contains(dept.getId()) || !Boolean.TRUE.equals(dept.getStatus())
                    || !Boolean.TRUE.equals(dept.getAllowShip())) {
                continue;
            }
            if (!Integer.valueOf(4).equals(dept.getDeptType()) && !Integer.valueOf(5).equals(dept.getDeptType())) {
                continue;
            }
            com.yunshu.modules.operation.vo.DeptOptionVO vo = new com.yunshu.modules.operation.vo.DeptOptionVO();
            vo.setId(dept.getId());
            vo.setDeptName(dept.getDeptName());
            vo.setDeptCode(dept.getDeptCode());
            vo.setDeptType(dept.getDeptType());
            result.add(vo);
        }
        return result;
    }

    /* ==================== 内部：校验与派生 ==================== */

    /** 送货方式必须符合所选线路的真实服务能力；3=大车直送不受服务能力限制 */
    private void validateRouteService(RouteCandidateVO route, Integer deliveryMethod) {
        if (deliveryMethod == null) {
            return;
        }
        if (deliveryMethod == 2 && Boolean.FALSE.equals(route.getPickupEnabled())) {
            throw new BizException(400, "该线路不提供自提服务");
        }
        if (deliveryMethod == 1 && Boolean.FALSE.equals(route.getDeliveryEnabled())) {
            throw new BizException(400, "该线路不提供送货服务");
        }
    }

    private boolean isChild(BizWaybill waybill) {
        return waybill.getIsChild() != null && waybill.getIsChild() == 1;
    }

    /** 详情/写操作统一入口：越权等同不存在；子单额外校验母单可见性 */
    private BizWaybill selectVisible(Long id) {
        BizWaybill waybill = id == null ? null : waybillMapper.selectById(id);
        if (waybill == null) {
            throw new BizException(403, "无权访问该运单");
        }
        if (isChild(waybill) && waybill.getParentId() != null
                && waybillMapper.selectById(waybill.getParentId()) == null) {
            throw new BizException(403, "无权访问该运单");
        }
        return waybill;
    }

    /**
     * 乐观锁令牌归一化：前端回传详情里的 updateTime（ISO 或 yyyy-MM-dd HH:mm:ss 均可）。
     * 当前 biz_waybill 无 version 列，以 update_time 作为并发令牌（见方案实现说明）。
     */
    private String normalizeVersion(String version) {
        String value = trimToNull(version);
        if (value == null) {
            throw new BizException("缺少并发版本号，请刷新后重试");
        }
        try {
            LocalDateTime time = value.contains("T")
                    ? LocalDateTime.parse(value.length() > 19 ? value.substring(0, 19) : value)
                    : LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ex) {
            throw new BizException("并发版本号格式非法，请刷新后重试");
        }
    }

    private String statusText(Integer status) {
        String label = WaybillEnums.Status.labelOf(status);
        return label == null ? "未知状态(" + status + ")" : label;
    }

    /** patch 校验：只校验 payload 里出现的字段；不再拦截空值（留空即置空） */
    private void validateAmend(WaybillAmendRequest request, Set<String> fields) {
        List<String> errors = new ArrayList<>();
        if (fields.contains("payMethod") && request.getPayMethod() != null
                && !WaybillEnums.PayMethod.isValid(request.getPayMethod())) {
            errors.add("支付方式码值非法");
        }
        if (fields.contains("deliveryMethod") && request.getDeliveryMethod() != null
                && !WaybillEnums.DeliveryMethod.isValid(request.getDeliveryMethod())) {
            errors.add("送货方式码值非法");
        }
        for (String key : FEE_FIELDS) {
            BigDecimal value = feeValue(request, key);
            if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(feeLabel(key) + "不能小于0");
            }
        }
        if (fields.contains("codAmount") && request.getCodAmount() != null
                && request.getCodAmount().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("代收货款不能小于0");
        }
        if (fields.contains("goodsList") && request.getGoodsList() != null) {
            for (int i = 0; i < request.getGoodsList().size(); i++) {
                WaybillSaveRequest.GoodsItem item = request.getGoodsList().get(i);
                if (item == null) {
                    continue;
                }
                String prefix = "第" + (i + 1) + "行货物";
                if (item.getQuantity() != null && item.getQuantity() < 0) {
                    errors.add(prefix + "件数不能小于0");
                }
                if (item.getWeight() != null && item.getWeight().compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(prefix + "重量不能小于0");
                }
                if (item.getVolume() != null && item.getVolume().compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(prefix + "体积不能小于0");
                }
            }
        }
        if (fields.contains("remark") && request.getRemark() != null && request.getRemark().length() > 500) {
            errors.add("备注不能超过500字");
        }
        if (!errors.isEmpty()) {
            throw new BizException(String.join("；", errors));
        }
    }

    private BigDecimal feeValue(WaybillAmendRequest request, String key) {
        return switch (key) {
            case "freightFee" -> request.getFreightFee();
            case "deliveryFee" -> request.getDeliveryFee();
            case "pickupFee" -> request.getPickupFee();
            case "otherFee" -> request.getOtherFee();
            case "receiptFee" -> request.getReceiptFee();
            case "makeBillFee" -> request.getMakeBillFee();
            case "insuranceFee" -> request.getInsuranceFee();
            case "cashBack" -> request.getCashBack();
            case "oweBack" -> request.getOweBack();
            default -> null;
        };
    }

    private String feeLabel(String key) {
        return switch (key) {
            case "freightFee" -> "运费";
            case "deliveryFee" -> "送货费";
            case "pickupFee" -> "提货费";
            case "otherFee" -> "其他费";
            case "receiptFee" -> "回单费";
            case "makeBillFee" -> "制单费";
            case "insuranceFee" -> "保费";
            case "cashBack" -> "现返";
            case "oweBack" -> "欠返";
            default -> key;
        };
    }

    /**
     * 字段级 diff（方案 8.5）：只比较 payload 中出现的字段。
     * region/goodsItems 为 null 表示该组未参与本次改单；amounts 为 null 表示不重算派生金额。
     */
    private List<WaybillFieldChangeVO> diffAmend(BizWaybill waybill, List<BizWaybillGoods> oldGoods,
                                                 WaybillAmendRequest request, Set<String> fields,
                                                 ReceiverRegion region,
                                                 WaybillAmountCalculator.AmountResult amounts,
                                                 Set<String> amountDirty,
                                                 List<WaybillSaveRequest.GoodsItem> goodsItems) {
        List<WaybillFieldChangeVO> changes = new ArrayList<>();
        if (fields.contains("senderCompany")) {
            addChange(changes, "senderCompany", "发货单位", waybill.getSenderCompany(),
                    trimToNull(request.getSenderCompany()));
        }
        if (fields.contains("senderName")) {
            addChange(changes, "senderName", "发货人", waybill.getSenderName(),
                    trimToNull(request.getSenderName()));
        }
        if (fields.contains("senderPhone")) {
            addChange(changes, "senderPhone", "发货电话", waybill.getSenderPhone(),
                    trimToNull(request.getSenderPhone()));
        }
        if (fields.contains("senderAddress")) {
            addChange(changes, "senderAddress", "发货地址", waybill.getSenderAddress(),
                    trimToNull(request.getSenderAddress()));
        }
        if (fields.contains("receiverCompany")) {
            addChange(changes, "receiverCompany", "收货单位", waybill.getReceiverCompany(),
                    trimToNull(request.getReceiverCompany()));
        }
        if (fields.contains("receiverName")) {
            addChange(changes, "receiverName", "收货人", waybill.getReceiverName(),
                    trimToNull(request.getReceiverName()));
        }
        if (fields.contains("receiverPhone")) {
            addChange(changes, "receiverPhone", "收货电话", waybill.getReceiverPhone(),
                    trimToNull(request.getReceiverPhone()));
        }
        if (region != null) {
            diffRegion(changes, waybill, region);
        }
        if (fields.contains("receiverAddress")) {
            addChange(changes, "receiverAddress", "收货地址", waybill.getReceiverAddress(),
                    trimToNull(request.getReceiverAddress()));
        }

        if (fields.contains("freightFee")) {
            addChange(changes, "freightFee", "运费", moneyText(waybill.getFreightFee()),
                    moneyText(request.getFreightFee()));
        }
        if (fields.contains("deliveryFee")) {
            addChange(changes, "deliveryFee", "送货费", moneyText(waybill.getDeliveryFee()),
                    moneyText(request.getDeliveryFee()));
        }
        if (fields.contains("pickupFee")) {
            addChange(changes, "pickupFee", "提货费", moneyText(waybill.getPickupFee()),
                    moneyText(request.getPickupFee()));
        }
        if (fields.contains("otherFee")) {
            addChange(changes, "otherFee", "其他费", moneyText(waybill.getOtherFee()),
                    moneyText(request.getOtherFee()));
        }
        if (fields.contains("receiptFee")) {
            addChange(changes, "receiptFee", "回单费", moneyText(waybill.getReceiptFee()),
                    moneyText(request.getReceiptFee()));
        }
        if (fields.contains("makeBillFee")) {
            addChange(changes, "makeBillFee", "制单费", moneyText(waybill.getMakeBillFee()),
                    moneyText(request.getMakeBillFee()));
        }
        if (fields.contains("insuranceFee")) {
            addChange(changes, "insuranceFee", "保费", moneyText(waybill.getInsuranceFee()),
                    moneyText(request.getInsuranceFee()));
        }
        if (fields.contains("cashBack")) {
            addChange(changes, "cashBack", "现返", moneyText(waybill.getCashBack()),
                    moneyText(request.getCashBack()));
        }
        if (fields.contains("oweBack")) {
            addChange(changes, "oweBack", "欠返", moneyText(waybill.getOweBack()),
                    moneyText(request.getOweBack()));
        }
        if (fields.contains("codAmount")) {
            addChange(changes, "codAmount", "代收货款", moneyText(waybill.getCodAmount()),
                    moneyText(request.getCodAmount()));
        }
        if (amounts != null) {
            if (amountDirty.contains("totalFee")) {
                addChange(changes, "totalFee", "运费合计", moneyText(waybill.getTotalFee()),
                        moneyText(amounts.totalFee()));
            }
            if (amountDirty.contains("rebateTotal")) {
                addChange(changes, "rebateTotal", "返款合计", moneyText(waybill.getRebateTotal()),
                        moneyText(amounts.rebateTotal()));
            }
            if (amountDirty.contains("validFreight")) {
                addChange(changes, "validFreight", "有效运费", moneyText(waybill.getValidFreight()),
                        moneyText(amounts.validFreight()));
            }
            if (amountDirty.contains("prepaidFee")) {
                addChange(changes, "prepaidFee", "现付运费", moneyText(waybill.getPrepaidFee()),
                        moneyText(amounts.prepaidFee()));
            }
            if (amountDirty.contains("collectFee")) {
                addChange(changes, "collectFee", "到付运费", moneyText(waybill.getCollectFee()),
                        moneyText(amounts.collectFee()));
            }
            if (amountDirty.contains("monthlyFee")) {
                addChange(changes, "monthlyFee", "月结运费", moneyText(waybill.getMonthlyFee()),
                        moneyText(amounts.monthlyFee()));
            }
            if (amountDirty.contains("returnFee")) {
                addChange(changes, "returnFee", "回付运费", moneyText(waybill.getReturnFee()),
                        moneyText(amounts.returnFee()));
            }
        }

        if (fields.contains("payMethod")) {
            addChange(changes, "payMethod", "支付方式", payMethodText(waybill.getPayMethod()),
                    payMethodText(request.getPayMethod()));
        }
        if (fields.contains("deliveryMethod")) {
            addChange(changes, "deliveryMethod", "送货方式", deliveryMethodText(waybill.getDeliveryMethod()),
                    deliveryMethodText(request.getDeliveryMethod()));
        }
        if (fields.contains("payeeName")) {
            addChange(changes, "payeeName", "收款人", waybill.getPayeeName(), trimToNull(request.getPayeeName()));
        }
        if (fields.contains("bankName")) {
            addChange(changes, "bankName", "开户行", waybill.getBankName(), trimToNull(request.getBankName()));
        }
        if (fields.contains("bankCardNo")) {
            addChange(changes, "bankCardNo", "银行卡号", maskCard(waybill.getBankCardNo()),
                    maskCard(request.getBankCardNo()));
        }
        if (fields.contains("remark")) {
            addChange(changes, "remark", "备注", waybill.getRemark(), trimToNull(request.getRemark()));
        }

        if (goodsItems != null) {
            if (oldGoods.size() != goodsItems.size()) {
                addChange(changes, "goodsRowCount", "货物明细行数",
                        String.valueOf(oldGoods.size()), String.valueOf(goodsItems.size()));
            }
            int rowCount = Math.min(oldGoods.size(), goodsItems.size());
            for (int i = 0; i < rowCount; i++) {
                BizWaybillGoods old = oldGoods.get(i);
                WaybillSaveRequest.GoodsItem item = goodsItems.get(i);
                if (item == null) {
                    continue;
                }
                String prefix = "货物明细第" + (i + 1) + "行·";
                addChange(changes, "goods[" + i + "].goodsName", prefix + "名称",
                        old.getGoodsName(), trimToNull(item.getGoodsName()));
                addChange(changes, "goods[" + i + "].goodsType", prefix + "类型",
                        old.getGoodsType(), trimToNull(item.getGoodsType()));
                addChange(changes, "goods[" + i + "].packageType", prefix + "包装",
                        old.getPackageType(), trimToNull(item.getPackageType()));
                addChange(changes, "goods[" + i + "].quantity", prefix + "件数",
                        String.valueOf(old.getQuantity()), String.valueOf(nzInt(item.getQuantity())));
                addChange(changes, "goods[" + i + "].weight", prefix + "重量(kg)",
                        scaleText(old.getWeight()), scaleText(nzDecimal(item.getWeight())));
                addChange(changes, "goods[" + i + "].volume", prefix + "体积(m³)",
                        scaleText(old.getVolume()), scaleText(nzDecimal(item.getVolume())));
                addChange(changes, "goods[" + i + "].goodsRemark", prefix + "备注",
                        old.getGoodsRemark(), trimToNull(item.getGoodsRemark()));
            }
        }
        return changes;
    }

    private void addChange(List<WaybillFieldChangeVO> changes, String field, String label,
                           String before, String after) {
        String oldValue = blankToNull(before);
        String newValue = blankToNull(after);
        if (java.util.Objects.equals(oldValue, newValue)) {
            return;
        }
        WaybillFieldChangeVO change = new WaybillFieldChangeVO();
        change.setField(field);
        change.setLabel(label);
        // 清空统一记 null（JSON 真 null），前端展示「—」
        change.setBefore(oldValue);
        change.setAfter(newValue);
        changes.add(change);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 快照契约（方案 13 / biz_waybill_log 注释）：waybill + children，键名与库表一致 */
    private Map<String, Object> snapshot(BizWaybill waybill) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("id", waybill.getId());
        main.put("no", waybill.getWaybillNo());
        main.put("status", waybill.getStatus());
        main.put("parent_id", waybill.getParentId());
        main.put("parent_no", waybill.getParentNo());
        root.put("waybill", main);

        List<Map<String, Object>> children = new ArrayList<>();
        if (!isChild(waybill)) {
            List<BizWaybill> rows = waybillMapper.selectList(Wrappers.<BizWaybill>lambdaQuery()
                    .eq(BizWaybill::getParentId, waybill.getId())
                    .orderByAsc(BizWaybill::getChildSeq)
                    .orderByAsc(BizWaybill::getId));
            for (BizWaybill child : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", child.getId());
                item.put("no", child.getWaybillNo());
                item.put("seq", child.getChildSeq());
                item.put("status", child.getStatus());
                item.put("quantity", child.getQuantity());
                item.put("weight", child.getWeight());
                item.put("volume", child.getVolume());
                children.add(item);
            }
        }
        root.put("children", children);
        return root;
    }

    /** 日志统一挂母单；子单操作也写母单 ID，与 biz_waybill_log 表注释一致 */
    private void writeLog(BizWaybill target, WaybillEnums.LogAction action, String reason,
                          Map<String, Object> before, Map<String, Object> after, DataScopeContext context) {
        boolean child = isChild(target);
        Long logWaybillId = child && target.getParentId() != null ? target.getParentId() : target.getId();
        String logNo = child && target.getParentNo() != null ? target.getParentNo() : target.getWaybillNo();
        BizWaybillLog log = new BizWaybillLog();
        log.setWaybillId(logWaybillId);
        log.setWaybillNo(logNo);
        log.setActionType(action.getCode());
        log.setBeforeSnapshot(toJson(before));
        log.setAfterSnapshot(toJson(after));
        log.setReason(reason);
        log.setAuditStatus(0);
        log.setOperatorId(requireUserId(context));
        log.setOperatorName(operatorName(context));
        log.setDeptId(context.getDeptId() != null ? context.getDeptId() : target.getDeptId());
        logMapper.insert(log);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(500, "操作快照序列化失败");
        }
    }

    private Long requireUserId(DataScopeContext context) {
        if (context.getUserId() == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        return context.getUserId();
    }

    private String operatorName(DataScopeContext context) {
        SysUser user = context.getUserId() == null ? null : userMapper.selectById(context.getUserId());
        if (user != null && StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        return StringUtils.hasText(context.getUsername()) ? context.getUsername() : "系统";
    }

    private String payMethodText(Integer code) {
        String label = WaybillEnums.PayMethod.labelOf(code);
        return label == null ? "" : label;
    }

    private String deliveryMethodText(Integer code) {
        String label = WaybillEnums.DeliveryMethod.labelOf(code);
        return label == null ? "" : label;
    }

    private String moneyText(BigDecimal value) {
        // 金额列清空统一按 0.00 记（不出现 null）
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private int nzInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nzDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String scaleText(BigDecimal value) {
        return value == null ? "" : value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private String maskCard(String card) {
        String value = trimToNull(card);
        if (value == null) {
            return null;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private void validate(WaybillSaveRequest request) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(request.getSenderName())) {
            errors.add("发货人不能为空");
        }
        if (!StringUtils.hasText(request.getSenderPhone())) {
            errors.add("发货电话不能为空");
        }
        if (!StringUtils.hasText(request.getSenderAddress())) {
            errors.add("发货地址不能为空");
        }
        if (!StringUtils.hasText(request.getReceiverName())) {
            errors.add("收货人不能为空");
        }
        if (!StringUtils.hasText(request.getReceiverPhone())) {
            errors.add("收货电话不能为空");
        }
        if (!StringUtils.hasText(request.getReceiverAddress())) {
            errors.add("收货地址不能为空");
        }
        if (!StringUtils.hasText(request.getDestDistrictCode())) {
            errors.add("请从候选列表中选择唯一目的地");
        }
        if (request.getRouteKeys() == null || request.getRouteKeys().isEmpty()) {
            errors.add("请选择线路");
        }
        if (request.getPayMethod() == null) {
            errors.add("请选择支付方式");
        }
        // 运费必填，允许 0（免费/月结整单免运费），不允许负数
        if (request.getFreightFee() == null) {
            errors.add("运费不能为空");
        } else if (request.getFreightFee().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("运费不能小于0");
        }
        if (request.getDeliveryMethod() == null) {
            // 未选送货方式只报这一条，不叠加送货费报错
            errors.add("请选择送货方式");
        } else if (request.getDeliveryMethod() < 1 || request.getDeliveryMethod() > 3) {
            errors.add("送货方式码值非法");
        } else if (request.getDeliveryMethod() == 1 || request.getDeliveryMethod() == 3) {
            // 送货(1)/大车直送(3) 送货费必填，允许 0；自提(2) 不要求
            if (request.getDeliveryFee() == null) {
                errors.add("送货方式为送货或大车直送时，送货费不能为空");
            } else if (request.getDeliveryFee().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("送货费不能小于0");
            }
        }
        if (request.getGoodsList() == null || request.getGoodsList().isEmpty()) {
            errors.add("请至少录入一行货物明细");
        } else {
            for (int i = 0; i < request.getGoodsList().size(); i++) {
                WaybillSaveRequest.GoodsItem item = request.getGoodsList().get(i);
                String prefix = "第" + (i + 1) + "行货物";
                if (item == null || !StringUtils.hasText(item.getGoodsName())) {
                    errors.add(prefix + "名称不能为空");
                    continue;
                }
                if (item.getQuantity() == null || item.getQuantity() < 1) {
                    errors.add(prefix + "件数必须为大于等于1的整数");
                }
                if (item.getWeight() == null || item.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(prefix + "重量必须大于0");
                }
                if (item.getVolume() == null || item.getVolume().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(prefix + "体积必须大于0");
                }
            }
        }
        if (request.getNeedReceipt() != null && request.getNeedReceipt() == 1
                && (request.getReceiptCount() == null || request.getReceiptCount() < 1)) {
            errors.add("是否回单为是时，回单份数必须大于等于1");
        }
        BigDecimal cod = request.getCodAmount() == null ? BigDecimal.ZERO : request.getCodAmount();
        if (cod.compareTo(BigDecimal.ZERO) > 0) {
            if (!StringUtils.hasText(request.getPayeeName())) {
                errors.add("代收货款大于0时，收款人不能为空");
            }
            if (!StringUtils.hasText(request.getBankName())) {
                errors.add("代收货款大于0时，开户行不能为空");
            }
            if (!StringUtils.hasText(request.getBankCardNo())) {
                errors.add("代收货款大于0时，银行卡号不能为空");
            }
        }
        if (!errors.isEmpty()) {
            throw new BizException(String.join("；", errors));
        }
    }

    /**
     * 开单网点固定取账号当前机构（顶部机构切换后的那个机构）。
     * 客户端传入的 originDeptId 一律忽略，避免越权开单或跨机构绕过。
     */
    private Long resolveOriginDept(Long requestedDeptId, DataScopeContext context) {
        Long deptId = context.getDeptId();
        if (deptId == null) {
            throw new BizException(403, "当前账号未分配开单网点");
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null || !Boolean.TRUE.equals(dept.getStatus())) {
            throw new BizException("当前机构不存在或已停用");
        }
        return deptId;
    }

    private RouteCandidateVO requireSelectedRoute(WaybillSaveRequest request, Long originDeptId) {
        List<RouteCandidateVO> candidates = routeCandidateService.candidates(
                request.getDestDistrictCode(), originDeptId);
        if (candidates.isEmpty()) {
            throw new BizException(42201, "未找到从当前网点到该目的地的路由，请检查短驳/干线配置");
        }
        List<String> submitted = request.getRouteKeys();
        return candidates.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getNoRoute()))
                .filter(item -> Objects.equals(item.getRouteKeys(), submitted))
                .findFirst()
                .orElseThrow(() -> new BizException(409, "路由已变更，请重新选择"));
    }

    private BizCustomer resolveCustomer(String senderName, String senderPhone) {
        CustomerMatchVO matched = customerMatchService.match(senderName, senderPhone);
        if (!Boolean.TRUE.equals(matched.getMatched()) || matched.getCandidates().isEmpty()) {
            return null;
        }
        Long id = matched.getCandidates().get(0).getId();
        return id == null ? null : customerMapper.selectById(id);
    }

    private record GoodsSummary(String name, String type, String packageType, Integer quantity,
                                BigDecimal weight, BigDecimal volume, String remark) {
    }

    private GoodsSummary summarize(List<WaybillSaveRequest.GoodsItem> items) {
        int quantity = 0;
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        LinkedHashSet<String> remarks = new LinkedHashSet<>();
        String type = null;
        String packageType = null;
        for (WaybillSaveRequest.GoodsItem item : items) {
            if (item == null) {
                continue;
            }
            quantity += nzInt(item.getQuantity());
            weight = weight.add(scale3(item.getWeight()));
            volume = volume.add(scale3(item.getVolume()));
            if (StringUtils.hasText(item.getGoodsName())) {
                names.add(item.getGoodsName().trim());
            }
            if (type == null && StringUtils.hasText(item.getGoodsType())) {
                type = item.getGoodsType().trim();
            }
            if (packageType == null && StringUtils.hasText(item.getPackageType())) {
                packageType = item.getPackageType().trim();
            }
            if (StringUtils.hasText(item.getGoodsRemark())) {
                remarks.add(item.getGoodsRemark().trim());
            }
        }
        return new GoodsSummary(truncate(String.join("、", names), 100), type, packageType, quantity,
                weight.setScale(3, RoundingMode.HALF_UP), volume.setScale(3, RoundingMode.HALF_UP),
                truncate(remarks.isEmpty() ? null : String.join("；", remarks), 500));
    }

    private void insertGoods(Long waybillId, Long deptId, List<WaybillSaveRequest.GoodsItem> items) {
        int seq = 1;
        for (WaybillSaveRequest.GoodsItem item : items) {
            BizWaybillGoods goods = new BizWaybillGoods();
            goods.setWaybillId(waybillId);
            goods.setSeq(seq++);
            goods.setGoodsName(item.getGoodsName().trim());
            goods.setGoodsType(trimToNull(item.getGoodsType()));
            goods.setPackageType(trimToNull(item.getPackageType()));
            goods.setQuantity(item.getQuantity());
            goods.setWeight(scale3(item.getWeight()));
            goods.setVolume(scale3(item.getVolume()));
            goods.setGoodsRemark(trimToNull(item.getGoodsRemark()));
            goods.setDeptId(deptId);
            goodsMapper.insert(goods);
        }
    }

    /**
     * 改单货物明细：按行原位更新，新增行插入，多余行物理删除。
     * biz_waybill_goods 唯一键 (waybill_id, seq) 不排除逻辑删除行，物理删除后才能复用序号。
     */
    private void replaceGoods(Long waybillId, Long deptId, List<WaybillSaveRequest.GoodsItem> items,
                              List<BizWaybillGoods> existing) {
        for (int i = 0; i < items.size(); i++) {
            WaybillSaveRequest.GoodsItem item = items.get(i);
            BizWaybillGoods row = i < existing.size() ? existing.get(i) : new BizWaybillGoods();
            row.setWaybillId(waybillId);
            row.setSeq(i + 1);
            row.setGoodsName(item.getGoodsName() == null ? "" : item.getGoodsName().trim());
            row.setGoodsType(trimToNull(item.getGoodsType()));
            row.setPackageType(trimToNull(item.getPackageType()));
            row.setQuantity(nzInt(item.getQuantity()));
            row.setWeight(scale3(item.getWeight()));
            row.setVolume(scale3(item.getVolume()));
            row.setGoodsRemark(trimToNull(item.getGoodsRemark()));
            if (row.getId() == null) {
                row.setDeptId(deptId);
                goodsMapper.insert(row);
            } else {
                goodsMapper.updateById(row);
            }
        }
        for (int i = items.size(); i < existing.size(); i++) {
            goodsMapper.physicalDeleteById(existing.get(i).getId());
        }
    }

    /** 双向校验：客户端若携带主表三量，必须与明细合计一致；不传则由服务端汇总覆盖 */
    private void assertMainTotalsMatch(WaybillSaveRequest request, GoodsSummary goods) {
        if (request.getQuantity() != null && !Objects.equals(request.getQuantity(), goods.quantity())) {
            throw new BizException("主表件数必须等于货物明细合计：请求 " + request.getQuantity()
                    + "，明细合计 " + goods.quantity());
        }
        if (request.getWeight() != null
                && request.getWeight().setScale(3, RoundingMode.HALF_UP).compareTo(goods.weight()) != 0) {
            throw new BizException("主表重量必须等于货物明细合计：请求 "
                    + request.getWeight().setScale(3, RoundingMode.HALF_UP).toPlainString()
                    + "，明细合计 " + goods.weight().toPlainString());
        }
        if (request.getVolume() != null
                && request.getVolume().setScale(3, RoundingMode.HALF_UP).compareTo(goods.volume()) != 0) {
            throw new BizException("主表体积必须等于货物明细合计：请求 "
                    + request.getVolume().setScale(3, RoundingMode.HALF_UP).toPlainString()
                    + "，明细合计 " + goods.volume().toPlainString());
        }
    }

    private record ReceiverRegion(String provinceCode, String provinceName, String cityCode,
                                  String cityName, String districtCode, String districtName) {
    }

    /** 收货地区只做结构校验（三码齐全 + 层级匹配）并派生名称；与收货详细地址文字不做强校验 */
    private ReceiverRegion resolveReceiverRegion(WaybillSaveRequest request) {
        String provinceCode = trimToNull(request.getReceiverProvinceCode());
        String cityCode = trimToNull(request.getReceiverCityCode());
        String districtCode = trimToNull(request.getReceiverDistrictCode());
        if (provinceCode == null && cityCode == null && districtCode == null) {
            return new ReceiverRegion(null, null, null, null, null, null);
        }
        if (provinceCode == null || cityCode == null || districtCode == null) {
            throw new BizException("收货省、市、区县需同时选择");
        }
        SysRegion district = regionMapper.selectById(districtCode);
        SysRegion city = regionMapper.selectById(cityCode);
        SysRegion province = regionMapper.selectById(provinceCode);
        if (district == null || city == null || province == null
                || !Integer.valueOf(3).equals(district.getLevel())
                || !Integer.valueOf(2).equals(city.getLevel())
                || !Integer.valueOf(1).equals(province.getLevel())
                || !cityCode.equals(district.getParentCode())
                || !provinceCode.equals(city.getParentCode())) {
            throw new BizException("收货省市区县编码层级不匹配，请重新选择");
        }
        return new ReceiverRegion(province.getCode(), province.getName(), city.getCode(), city.getName(),
                district.getCode(), district.getName());
    }

    private void insertNodes(Long waybillId, RouteCandidateVO route, Long deptId) {
        for (RouteCandidateVO.Node item : route.getNodes()) {
            BizWaybillRouteNode node = new BizWaybillRouteNode();
            node.setWaybillId(waybillId);
            node.setSeq(item.getSeq());
            node.setRouteScope(item.getRouteScope());
            node.setRouteId(item.getRouteId());
            node.setRouteCode(item.getRouteCode());
            node.setRouteCategory(item.getRouteCategory());
            node.setTransferType(item.getTransferType());
            node.setFromDeptId(item.getFromDeptId());
            node.setFromDeptName(item.getFromDeptName());
            node.setToDeptId(item.getToDeptId());
            node.setToDeptName(item.getToDeptName());
            node.setDestRegionCode(item.getDestRegionCode());
            node.setDestRegionName(item.getDestRegionName());
            node.setCarrierId(item.getCarrierId());
            node.setCarrierName(item.getCarrierName());
            node.setWarehouseArea(item.getWarehouseArea());
            node.setMileage(item.getMileage());
            node.setTransitHours(item.getTransitHours());
            node.setDeptId(deptId);
            nodeMapper.insert(node);
        }
    }

    private WaybillSaveVO toSaveVO(BizWaybill waybill) {
        WaybillSaveVO vo = new WaybillSaveVO();
        vo.setId(waybill.getId());
        vo.setWaybillNo(waybill.getWaybillNo());
        vo.setStatus(waybill.getStatus());
        vo.setPayMethod(waybill.getPayMethod());
        vo.setTotalFee(waybill.getTotalFee());
        vo.setRebateTotal(waybill.getRebateTotal());
        vo.setValidFreight(waybill.getValidFreight());
        vo.setPrepaidFee(waybill.getPrepaidFee());
        vo.setCollectFee(waybill.getCollectFee());
        vo.setMonthlyFee(waybill.getMonthlyFee());
        vo.setReturnFee(waybill.getReturnFee());
        vo.setCustomerId(waybill.getCustomerId());
        vo.setCustomerName(waybill.getCustomerName());
        vo.setOriginHubId(waybill.getOriginHubId());
        vo.setOriginHubName(waybill.getOriginHubName());
        vo.setDestHubId(waybill.getDestHubId());
        vo.setDestHubName(waybill.getDestHubName());
        vo.setRouteName(waybill.getRouteName());
        vo.setRouteNodeCount(waybill.getRouteNodeCount());
        vo.setRouteEndType(waybill.getRouteEndType());
        vo.setDestName(waybill.getDestName());
        vo.setCreateTime(waybill.getCreateTime());
        return vo;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? new BigDecimal("0.00") : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale3(BigDecimal value) {
        return value == null ? new BigDecimal("0.000") : value.setScale(3, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
