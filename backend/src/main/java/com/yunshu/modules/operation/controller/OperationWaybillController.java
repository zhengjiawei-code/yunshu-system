package com.yunshu.modules.operation.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.operation.dto.WaybillSaveRequest;
import com.yunshu.modules.operation.service.CustomerMatchService;
import com.yunshu.modules.operation.service.PreOrderService;
import com.yunshu.modules.operation.service.RouteCandidateService;
import com.yunshu.modules.operation.service.WaybillService;
import com.yunshu.modules.operation.vo.CustomerMatchVO;
import com.yunshu.modules.operation.vo.DeptOptionVO;
import com.yunshu.modules.operation.vo.PreOrderPageVO;
import com.yunshu.modules.operation.vo.RouteCandidateVO;
import com.yunshu.modules.operation.vo.WaybillDetailVO;
import com.yunshu.modules.operation.vo.WaybillSaveVO;
import com.yunshu.modules.operation.entity.BizPreWaybill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 运营管理 · 开单揽收：目的地候选、路由候选、客户匹配、开单保存、预开单只读 */
@Tag(name = "运营管理-开单揽收")
@RestController
@RequestMapping("/api/v1/operation")
@RequiredArgsConstructor
public class OperationWaybillController {

    private final WaybillService waybillService;
    private final RouteCandidateService routeCandidateService;
    private final CustomerMatchService customerMatchService;
    private final PreOrderService preOrderService;

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

    @Operation(summary = "开单保存：服务端重算金额与路由、派生网点，客户端传值无效")
    @PostMapping("/waybills")
    public ApiResult<WaybillSaveVO> create(@RequestBody WaybillSaveRequest request) {
        return ApiResult.ok(waybillService.create(request));
    }

    @Operation(summary = "运单详情（含路由节点快照；越权 403）")
    @GetMapping("/waybills/{id}")
    public ApiResult<WaybillDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(waybillService.detail(id));
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
