package com.yunshu.modules.operation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.operation.dto.WaybillAmendRequest;
import com.yunshu.modules.operation.dto.WaybillListQuery;
import com.yunshu.modules.operation.dto.WaybillSaveRequest;
import com.yunshu.modules.operation.dto.WaybillVoidRequest;
import com.yunshu.modules.operation.service.CustomerMatchService;
import com.yunshu.modules.operation.service.PreOrderService;
import com.yunshu.modules.operation.service.RouteCandidateService;
import com.yunshu.modules.operation.service.WaybillQueryService;
import com.yunshu.modules.operation.service.WaybillService;
import com.yunshu.modules.operation.vo.CustomerMatchVO;
import com.yunshu.modules.operation.vo.DeptOptionVO;
import com.yunshu.modules.operation.vo.PreOrderPageVO;
import com.yunshu.modules.operation.vo.RouteCandidateVO;
import com.yunshu.modules.operation.vo.WaybillAmendVO;
import com.yunshu.modules.operation.vo.WaybillChildrenVO;
import com.yunshu.modules.operation.vo.WaybillDetailVO;
import com.yunshu.modules.operation.vo.WaybillPageVO;
import com.yunshu.modules.operation.vo.WaybillSaveVO;
import com.yunshu.modules.operation.vo.WaybillVoidVO;
import com.yunshu.modules.operation.entity.BizPreWaybill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 运营管理 · 开单揽收：目的地候选、路由候选、客户匹配、开单保存、预开单只读 */
@Tag(name = "运营管理-开单揽收")
@RestController
@RequestMapping("/api/v1/operation")
@RequiredArgsConstructor
public class OperationWaybillController {

    private final WaybillService waybillService;
    private final WaybillQueryService waybillQueryService;
    private final RouteCandidateService routeCandidateService;
    private final CustomerMatchService customerMatchService;
    private final PreOrderService preOrderService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "可选开单网点（管理范围内的启用分拨中心/网点）")
    @GetMapping("/open-depts")
    public ApiResult<List<DeptOptionVO>> openDepts() {
        return ApiResult.ok(waybillService.openDepts());
    }

    @Operation(summary = "目的地候选：区县/市/省关键字匹配，返回目的地 × 可行路线；节点数升序→直达优先")
    @GetMapping("/destinations")
    public ApiResult<List<RouteCandidateVO>> destinations(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Long originDeptId) {
        return ApiResult.ok(routeCandidateService.searchDestinations(keyword, originDeptId));
    }

    @Operation(summary = "指定区县的路由候选（切换线路 / 保存二次校验共用）")
    @GetMapping("/route-candidates")
    public ApiResult<List<RouteCandidateVO>> routeCandidates(@RequestParam String destDistrictCode,
                                                             @RequestParam(required = false) Long originDeptId) {
        return ApiResult.ok(routeCandidateService.candidates(destDistrictCode, originDeptId));
    }

    @Operation(summary = "客户自动匹配：发货人名称精确匹配，同名时电话消歧；未命中返回空数组")
    @GetMapping("/customers/match")
    public ApiResult<CustomerMatchVO> customerMatch(@RequestParam(required = false) String senderName,
                                                    @RequestParam(required = false) String senderPhone) {
        return ApiResult.ok(customerMatchService.match(senderName, senderPhone));
    }

    @Operation(summary = "运单列表：只返回母单，分页/排序/列筛选/合计，数据权限由拦截器追加")
    @GetMapping("/waybills")
    public ApiResult<WaybillPageVO> page(@RequestParam Map<String, String> params) {
        return ApiResult.ok(waybillQueryService.page(WaybillListQuery.from(params)));
    }

    @Operation(summary = "开单保存：服务端重算金额与路由、派生网点，客户端传值无效")
    @PostMapping("/waybills")
    public ApiResult<WaybillSaveVO> create(@RequestBody WaybillSaveRequest request) {
        return ApiResult.ok(waybillService.create(request));
    }

    @Operation(summary = "运单详情（含路由节点、货物明细、操作日志、改单记录；越权 403）")
    @GetMapping("/waybills/{id}")
    public ApiResult<WaybillDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(waybillService.detail(id));
    }

    @Operation(summary = "子单清单：母单与子单三量合计，不一致由前端红标")
    @GetMapping("/waybills/{id}/children")
    public ApiResult<WaybillChildrenVO> children(@PathVariable Long id) {
        return ApiResult.ok(waybillQueryService.children(id));
    }

    @Operation(summary = "作废：一期任意状态可作废，原因必填，写 action_type=4 日志")
    @PostMapping("/waybills/{id}/void")
    public ApiResult<WaybillVoidVO> voidWaybill(@PathVariable Long id,
                                                @RequestBody WaybillVoidRequest request) {
        return ApiResult.ok(waybillService.voidWaybill(id, request));
    }

    @Operation(summary = "改单：patch 语义，只写 payload 出现的字段；状态 20–90；金额重算；字段级 diff 日志")
    @PutMapping("/waybills/{id}/amend")
    public ApiResult<WaybillAmendVO> amend(@PathVariable Long id,
                                          @RequestBody JsonNode body) throws JsonProcessingException {
        Set<String> present = new LinkedHashSet<>();
        body.fieldNames().forEachRemaining(present::add);
        WaybillAmendRequest request = objectMapper.treeToValue(body, WaybillAmendRequest.class);
        return ApiResult.ok(waybillService.amend(id, request, present));
    }

    @Operation(summary = "预开单只读列表（本阶段占位）")
    @GetMapping("/pre-orders")
    public ApiResult<PreOrderPageVO> preOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime submitStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime submitEnd,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(preOrderService.page(status, keyword, submitStart, submitEnd, sortOrder, page, pageSize));
    }

    @Operation(summary = "预开单详情（本阶段占位）")
    @GetMapping("/pre-orders/{id}")
    public ApiResult<BizPreWaybill> preOrderDetail(@PathVariable Long id) {
        return ApiResult.ok(preOrderService.detail(id));
    }
}
