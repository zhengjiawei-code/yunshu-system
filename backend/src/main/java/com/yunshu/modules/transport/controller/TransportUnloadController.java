package com.yunshu.modules.transport.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.transport.dto.ArrivalFeeRequest;
import com.yunshu.modules.transport.dto.ArriveRequest;
import com.yunshu.modules.transport.dto.CancelUnloadRequest;
import com.yunshu.modules.transport.dto.ReasonRequest;
import com.yunshu.modules.transport.dto.UnloadRequest;
import com.yunshu.modules.transport.service.TripUnloadService;
import com.yunshu.modules.transport.vo.ArrivalFeeVO;
import com.yunshu.modules.transport.vo.DestActionVO;
import com.yunshu.modules.transport.vo.UnloadPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 运输管理 · 到站卸车（B4）：卸车页、到站、卸车、到站费用、取消到站/取消卸车。
 */
@Tag(name = "运输管理-到站卸车")
@RestController
@RequestMapping("/api/v1/transport")
@RequiredArgsConstructor
public class TransportUnloadController {

    private final TripUnloadService unloadService;

    @Operation(summary = "到站卸车页：信息条 + Tab 计数 + 运单列表 + 费用区（排除大车直送）")
    @GetMapping("/trip/{tripId}/dests/{destId}/unload")
    public ApiResult<UnloadPageVO> page(@PathVariable Long tripId,
                                        @PathVariable Long destId,
                                        @RequestParam(required = false) String tab,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer pageSize) {
        return ApiResult.ok(unloadService.page(tripId, destId, tab, keyword, page, pageSize));
    }

    @Operation(summary = "确认到站：运单 80→90（分拨中心 50），主车次按剩余算 40/50")
    @PostMapping("/trip/{tripId}/dests/{destId}/arrive")
    public ApiResult<DestActionVO> arrive(@PathVariable Long tripId,
                                          @PathVariable Long destId,
                                          @RequestBody(required = false) ArriveRequest request) {
        return ApiResult.ok(unloadService.arrive(tripId, destId, request));
    }

    @Operation(summary = "卸车：按 D9 映射 100/110/120（分拨中心 60 回池），全部卸完子车次 40")
    @PostMapping("/trip/{tripId}/dests/{destId}/unload")
    public ApiResult<DestActionVO> unload(@PathVariable Long tripId,
                                          @PathVariable Long destId,
                                          @RequestBody(required = false) UnloadRequest request) {
        return ApiResult.ok(unloadService.unload(tripId, destId, request));
    }

    @Operation(summary = "保存到站卸车费 + 到站其他费（只摊本子车次非大车直送）")
    @PutMapping("/trip/{tripId}/dests/{destId}/arrival-fees")
    public ApiResult<ArrivalFeeVO> saveArrivalFees(@PathVariable Long tripId,
                                                   @PathVariable Long destId,
                                                   @RequestBody @Valid ArrivalFeeRequest request) {
        return ApiResult.ok(unloadService.saveArrivalFees(tripId, destId, request));
    }

    @Operation(summary = "到站费用预览（只读，不落库）")
    @GetMapping("/trip/{tripId}/dests/{destId}/arrival-fees/preview")
    public ApiResult<ArrivalFeeVO> previewArrivalFees(@PathVariable Long tripId,
                                                      @PathVariable Long destId,
                                                      @RequestParam(required = false) BigDecimal unloadFee,
                                                      @RequestParam(required = false) BigDecimal otherFee) {
        return ApiResult.ok(unloadService.previewArrivalFees(tripId, destId, unloadFee, otherFee));
    }

    @Operation(summary = "取消到站：仅未卸车；运单 90/50→80、到站费用标记失效")
    @PostMapping("/trip/{tripId}/dests/{destId}/cancel-arrive")
    public ApiResult<DestActionVO> cancelArrive(@PathVariable Long tripId,
                                                @PathVariable Long destId,
                                                @RequestBody @Valid ReasonRequest request) {
        return ApiResult.ok(unloadService.cancelArrive(tripId, destId, request.getReason()));
    }

    @Operation(summary = "取消卸车：逐票回退状态与库存，到站费用标记失效")
    @PostMapping("/trip/{tripId}/dests/{destId}/cancel-unload")
    public ApiResult<DestActionVO> cancelUnload(@PathVariable Long tripId,
                                                @PathVariable Long destId,
                                                @RequestBody @Valid CancelUnloadRequest request) {
        return ApiResult.ok(unloadService.cancelUnload(tripId, destId, request));
    }
}
