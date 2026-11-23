package com.yunshu.modules.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.exception.BizException;
import com.yunshu.modules.operation.dto.WaybillSaveRequest;
import com.yunshu.modules.operation.entity.BizCustomer;
import com.yunshu.modules.operation.entity.BizWaybill;
import com.yunshu.modules.operation.entity.BizWaybillGoods;
import com.yunshu.modules.operation.entity.BizWaybillRouteNode;
import com.yunshu.modules.operation.mapper.BizCustomerMapper;
import com.yunshu.modules.operation.mapper.BizWaybillGoodsMapper;
import com.yunshu.modules.operation.mapper.BizWaybillMapper;
import com.yunshu.modules.operation.mapper.BizWaybillRouteNodeMapper;
import com.yunshu.modules.operation.vo.CustomerMatchVO;
import com.yunshu.modules.operation.vo.RouteCandidateVO;
import com.yunshu.modules.operation.vo.WaybillDetailVO;
import com.yunshu.modules.operation.vo.WaybillSaveVO;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final BizCustomerMapper customerMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysRegionMapper regionMapper;
    private final UserOrgService userOrgService;
    private final CustomerMatchService customerMatchService;
    private final RouteCandidateService routeCandidateService;
    private final WaybillAmountCalculator amountCalculator;
    private final WaybillNoGenerator noGenerator;

    @Transactional(rollbackFor = Exception.class)
    public WaybillSaveVO create(WaybillSaveRequest request) {
        DataScopeContext context = DataScopeContext.require("开单保存");
        validate(request);
        Long originDeptId = resolveOriginDept(request.getOriginDeptId(), context);
        SysDept originDept = deptMapper.selectById(originDeptId);

        RouteCandidateVO route = requireSelectedRoute(request, originDeptId);
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

    /** 详情：数据权限由拦截器追加 dept_id 条件，越权等同不存在 */
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
        WaybillDetailVO vo = new WaybillDetailVO();
        vo.setWaybill(waybill);
        vo.setNodes(nodes);
        vo.setGoods(goods);
        return vo;
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
        if (request.getDeliveryMethod() == null) {
            errors.add("请选择送货方式");
        } else if (request.getDeliveryMethod() < 1 || request.getDeliveryMethod() > 3) {
            errors.add("送货方式码值非法");
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

    private Long resolveOriginDept(Long requestedDeptId, DataScopeContext context) {
        Long deptId = requestedDeptId != null ? requestedDeptId : context.getDeptId();
        if (deptId == null) {
            throw new BizException(403, "当前账号未分配开单网点");
        }
        Set<Long> allowed = userOrgService.manageDeptIds();
        if (!allowed.contains(deptId)) {
            throw new BizException(403, "开单网点不在当前账号管理范围内，禁止跨机构开单");
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null || !Boolean.TRUE.equals(dept.getStatus())) {
            throw new BizException("开单网点不存在或已停用");
        }
        if (!Integer.valueOf(4).equals(dept.getDeptType()) && !Integer.valueOf(5).equals(dept.getDeptType())) {
            throw new BizException("开单网点必须为分拨中心或网点");
        }
        if (!Boolean.TRUE.equals(dept.getAllowShip())) {
            throw new BizException("该网点不允许发货");
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
            quantity += item.getQuantity();
            weight = weight.add(scale3(item.getWeight()));
            volume = volume.add(scale3(item.getVolume()));
            names.add(item.getGoodsName().trim());
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
        return value.setScale(3, RoundingMode.HALF_UP);
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
