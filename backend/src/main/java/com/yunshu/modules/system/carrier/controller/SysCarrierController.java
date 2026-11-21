package com.yunshu.modules.system.carrier.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.system.carrier.dto.CarrierQuery;
import com.yunshu.modules.system.carrier.dto.CarrierSaveRequest;
import com.yunshu.modules.system.carrier.service.SysCarrierService;
import com.yunshu.modules.system.carrier.vo.CarrierPageVO;
import com.yunshu.modules.system.carrier.vo.CarrierVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 承运商管理：无授权概念，可见性由服务层按管理范围显式过滤（豁免数据权限）。
 */
@Tag(name = "系统管理-承运商")
@RestController
@RequestMapping("/api/v1/system/carriers")
@RequiredArgsConstructor
public class SysCarrierController {

    private final SysCarrierService carrierService;

    @Operation(summary = "分页查询承运商（服务端筛选、排序、分页）")
    @GetMapping("/page")
    public ApiResult<CarrierPageVO> page(CarrierQuery query) {
        return ApiResult.ok(carrierService.page(query));
    }

    @Operation(summary = "启用承运商下拉（路由外转选择用，按管理范围过滤）")
    @GetMapping("/options")
    public ApiResult<List<CarrierVO>> options() {
        return ApiResult.ok(carrierService.options());
    }

    @Operation(summary = "承运商详情（越权返回 403）")
    @GetMapping("/{id}")
    public ApiResult<CarrierVO> detail(@PathVariable Long id) {
        return ApiResult.ok(carrierService.detail(id));
    }

    @Operation(summary = "新增承运商（归属机构取当前主机构，只读）")
    @PostMapping
    public ApiResult<Long> create(@RequestBody @Valid CarrierSaveRequest request) {
        return ApiResult.ok(carrierService.create(request));
    }

    @Operation(summary = "编辑承运商（仅管理范围内）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid CarrierSaveRequest request) {
        carrierService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "删除承运商（被路由引用时拒绝）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        carrierService.delete(id);
        return ApiResult.ok();
    }
}
