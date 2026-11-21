package com.yunshu.modules.system.route.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.system.route.dto.BatchGrantRequest;
import com.yunshu.modules.system.route.dto.GrantRequest;
import com.yunshu.modules.system.route.dto.RouteQuery;
import com.yunshu.modules.system.route.dto.RouteSaveRequest;
import com.yunshu.modules.system.route.dto.TransferBatchCarrierRequest;
import com.yunshu.modules.system.route.dto.TransferBatchJoinRequest;
import com.yunshu.modules.system.route.dto.TransferBatchStatusRequest;
import com.yunshu.modules.system.route.dto.TransferBatchTypeRequest;
import com.yunshu.modules.system.route.dto.TransferEditRequest;
import com.yunshu.modules.system.route.dto.TransferQuery;
import com.yunshu.modules.system.route.service.SysRouteService;
import com.yunshu.modules.system.route.vo.BatchGrantResultVO;
import com.yunshu.modules.system.route.vo.RegionLibraryPageVO;
import com.yunshu.modules.system.route.vo.RouteGrantVO;
import com.yunshu.modules.system.route.vo.RouteMatchVO;
import com.yunshu.modules.system.route.vo.RoutePageVO;
import com.yunshu.modules.system.route.vo.RouteSaveResultVO;
import com.yunshu.modules.system.route.vo.RouteTransferVO;
import com.yunshu.modules.system.route.vo.RouteVO;
import com.yunshu.modules.system.route.vo.TransferBatchResultVO;
import com.yunshu.modules.system.route.vo.TransferPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 路由管理：Tab1 短驳/干线，Tab2 外转/直达；授权仅总部可写。
 * 所有查询由服务层按授权关系显式过滤（四张路由表已豁免数据权限）。
 */
@Tag(name = "系统管理-路由")
@RestController
@RequestMapping("/api/v1/system/routes")
@RequiredArgsConstructor
public class SysRouteController {

    private final SysRouteService routeService;

    /* ---------- Tab1 短驳/干线 ---------- */

    @Operation(summary = "短驳/干线路由分页（服务端筛选、排序、分页）")
    @GetMapping("/page")
    public ApiResult<RoutePageVO> page(RouteQuery query) {
        return ApiResult.ok(routeService.page(query));
    }

    @Operation(summary = "短驳/干线路由详情（越权 403）")
    @GetMapping("/{id}")
    public ApiResult<RouteVO> detail(@PathVariable Long id) {
        return ApiResult.ok(routeService.detail(id));
    }

    @Operation(summary = "新增短驳/干线路由（同键只补授权不覆盖公共字段）")
    @PostMapping
    public ApiResult<RouteSaveResultVO> create(@RequestBody @Valid RouteSaveRequest request) {
        return ApiResult.ok(routeService.create(request));
    }

    @Operation(summary = "编辑短驳/干线路由（仅创建公司或总部）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid RouteSaveRequest request) {
        routeService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "取消路由（存在其他授权时拒绝）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        routeService.delete(id);
        return ApiResult.ok();
    }

    /* ---------- Tab2 外转/直达 ---------- */

    @Operation(summary = "外转/直达路由分页")
    @GetMapping("/transfers/page")
    public ApiResult<TransferPageVO> transferPage(TransferQuery query) {
        return ApiResult.ok(routeService.transferPage(query));
    }

    @Operation(summary = "外转/直达路由详情")
    @GetMapping("/transfers/{id}")
    public ApiResult<RouteTransferVO> transferDetail(@PathVariable Long id) {
        return ApiResult.ok(routeService.transferDetail(id));
    }

    @Operation(summary = "区划库（按配置网点返回可加入区划与配置状态）")
    @GetMapping("/transfers/region-library")
    public ApiResult<RegionLibraryPageVO> regionLibrary(
            @RequestParam(required = false) Long applyDeptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<Integer> level,
            @RequestParam(required = false) List<Boolean> configured,
            @RequestParam(required = false) String parentCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String districtKeyword,
            @RequestParam(required = false) String townKeyword,
            @RequestParam(required = false) String filterModes,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(routeService.regionLibrary(applyDeptId, keyword, level, configured,
                parentCode, provinceCode, cityCode, districtKeyword, townKeyword,
                filterModes, sortBy, sortOrder, pageNum, pageSize));
    }

    @Operation(summary = "从区划库批量加入（默认外转；本公司覆盖公共字段，他公司只补授权）")
    @PostMapping("/transfers/batch-join")
    public ApiResult<TransferBatchResultVO> batchJoin(@RequestBody @Valid TransferBatchJoinRequest request) {
        return ApiResult.ok(routeService.batchJoin(request));
    }

    @Operation(summary = "编辑外转/直达路由（仅名称/承运商/状态/排序/备注）")
    @PutMapping("/transfers/{id}")
    public ApiResult<Void> updateTransfer(@PathVariable Long id, @RequestBody @Valid TransferEditRequest request) {
        routeService.updateTransfer(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "批量切换直达/外转（切直达清空承运商并重写编码后缀）")
    @PutMapping("/transfers/batch/type")
    public ApiResult<TransferBatchResultVO> switchTransferType(@RequestBody @Valid TransferBatchTypeRequest request) {
        return ApiResult.ok(routeService.switchTransferType(request));
    }

    @Operation(summary = "批量挂载承运商（直达跳过）")
    @PutMapping("/transfers/batch/carrier")
    public ApiResult<TransferBatchResultVO> batchCarrier(@RequestBody @Valid TransferBatchCarrierRequest request) {
        return ApiResult.ok(routeService.batchCarrier(request));
    }

    @Operation(summary = "批量启用/停用")
    @PutMapping("/transfers/batch/status")
    public ApiResult<TransferBatchResultVO> batchStatus(@RequestBody @Valid TransferBatchStatusRequest request) {
        return ApiResult.ok(routeService.batchStatus(request));
    }

    @Operation(summary = "取消外转/直达路由")
    @DeleteMapping("/transfers/{id}")
    public ApiResult<Void> deleteTransfer(@PathVariable Long id) {
        routeService.deleteTransfer(id);
        return ApiResult.ok();
    }

    /* ---------- 授权 ---------- */

    @Operation(summary = "查询路由授权列表（routeScope 1=短驳/干线 2=外转/直达）")
    @GetMapping("/{id}/grants")
    public ApiResult<List<RouteGrantVO>> grants(@PathVariable Long id,
                                                @RequestParam Integer routeScope) {
        return ApiResult.ok(routeService.grants(routeScope, id));
    }

    @Operation(summary = "手动授权（仅总部；0 号行存在时拒绝）")
    @PostMapping("/{id}/grants")
    public ApiResult<Integer> grant(@PathVariable Long id,
                                    @RequestParam Integer routeScope,
                                    @RequestBody @Valid GrantRequest request) {
        return ApiResult.ok(routeService.grant(routeScope, id, request.getCompanyDeptIds()));
    }

    @Operation(summary = "取消授权（分公司仅能取消本公司；删至无授权自动停用路由）")
    @DeleteMapping("/{id}/grants/{companyDeptId}")
    public ApiResult<Void> revoke(@PathVariable Long id, @PathVariable Long companyDeptId,
                                  @RequestParam Integer routeScope) {
        routeService.revoke(routeScope, id, companyDeptId);
        return ApiResult.ok();
    }

    @Operation(summary = "批量授权（仅总部，50×50 上限，重复跳过）")
    @PostMapping("/grants/batch")
    public ApiResult<BatchGrantResultVO> batchGrant(@RequestBody @Valid BatchGrantRequest request) {
        return ApiResult.ok(routeService.batchGrant(request));
    }

    /* ---------- 开单匹配 ---------- */

    @Operation(summary = "开单路由匹配（直达>网络完整链路>外转；首条推荐；无候选返回阻断文案）")
    @GetMapping("/match")
    public ApiResult<RouteMatchVO> match(@RequestParam(required = false) String destRegionCode,
                                         @RequestParam(required = false) Long destDeptId,
                                         @RequestParam(required = false) Long originDeptId,
                                         @RequestParam(required = false) Long applyDeptId) {
        return ApiResult.ok(routeService.match(destRegionCode, destDeptId, originDeptId, applyDeptId));
    }
}
