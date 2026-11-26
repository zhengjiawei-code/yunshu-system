package com.yunshu.modules.transport.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.transport.dto.QuickDriverRequest;
import com.yunshu.modules.transport.dto.QuickVehicleRequest;
import com.yunshu.modules.transport.dto.TripDestSaveRequest;
import com.yunshu.modules.transport.dto.TripListQuery;
import com.yunshu.modules.transport.dto.TripSaveRequest;
import com.yunshu.modules.transport.dto.TripUpdateRequest;
import com.yunshu.modules.transport.service.TripService;
import com.yunshu.modules.transport.vo.TripDestVO;
import com.yunshu.modules.transport.vo.TripDetailVO;
import com.yunshu.modules.transport.vo.TripOptionVO;
import com.yunshu.modules.transport.vo.TripPageVO;
import com.yunshu.modules.transport.vo.TripSaveVO;
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
import java.util.Map;

/**
 * 运输管理 · 装车配载（B1）：车次列表/详情/CRUD、目的网点维护、下拉与车辆司机快速新增。
 */
@Tag(name = "运输管理-装车配载")
@RestController
@RequestMapping("/api/v1/transport")
@RequiredArgsConstructor
public class TransportTripController {

    private final TripService tripService;

    @Operation(summary = "车次分页列表（数据权限按 dept_id 过滤）")
    @GetMapping("/trip/page")
    public ApiResult<TripPageVO> page(@RequestParam Map<String, String> params) {
        return ApiResult.ok(tripService.page(TripListQuery.from(params)));
    }

    @Operation(summary = "车次下拉选项：dept / vehicle / driver / vehicleType")
    @GetMapping("/trip/options")
    public ApiResult<List<TripOptionVO>> options(@RequestParam(defaultValue = "dept") String type,
                                                 @RequestParam(required = false) String keyword) {
        return ApiResult.ok(tripService.options(type, keyword));
    }

    @Operation(summary = "车次详情（含子车次与汇总；越权 403）")
    @GetMapping("/trip/{id}")
    public ApiResult<TripDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(tripService.detail(id));
    }

    @Operation(summary = "创建车次：生成车次号与子车次")
    @PostMapping("/trip")
    public ApiResult<TripSaveVO> create(@RequestBody @Valid TripSaveRequest request) {
        return ApiResult.ok(tripService.create(request));
    }

    @Operation(summary = "修改车次：仅车次计划/装车配载状态")
    @PutMapping("/trip/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid TripUpdateRequest request) {
        tripService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "删除车次：仅车次计划且无配载")
    @DeleteMapping("/trip/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        tripService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "新增目的网点（发车后新增子车次置在途）")
    @PostMapping("/trip/{id}/dests")
    public ApiResult<TripDestVO> addDest(@PathVariable Long id,
                                         @RequestBody @Valid TripDestSaveRequest request) {
        return ApiResult.ok(tripService.addDest(id, request));
    }

    @Operation(summary = "修改目的网点/顺序/计划到站时间（网点无配载）")
    @PutMapping("/trip/{id}/dests/{destId}")
    public ApiResult<Void> updateDest(@PathVariable Long id, @PathVariable Long destId,
                                      @RequestBody TripDestSaveRequest request) {
        tripService.updateDest(id, destId, request);
        return ApiResult.ok();
    }

    @Operation(summary = "删除目的网点（网点无配载，至少保留一个）")
    @DeleteMapping("/trip/{id}/dests/{destId}")
    public ApiResult<Void> deleteDest(@PathVariable Long id, @PathVariable Long destId) {
        tripService.deleteDest(id, destId);
        return ApiResult.ok();
    }

    @Operation(summary = "车辆快速新增（同车牌同机构幂等返回）")
    @PostMapping("/vehicle/quick")
    public ApiResult<Long> quickVehicle(@RequestBody @Valid QuickVehicleRequest request) {
        return ApiResult.ok(tripService.quickVehicle(request));
    }

    @Operation(summary = "司机快速新增（同名同电话同机构幂等返回）")
    @PostMapping("/driver/quick")
    public ApiResult<Long> quickDriver(@RequestBody @Valid QuickDriverRequest request) {
        return ApiResult.ok(tripService.quickDriver(request));
    }
}
