package com.yunshu.modules.transport.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.transport.dto.DepartRequest;
import com.yunshu.modules.transport.dto.ReasonRequest;
import com.yunshu.modules.transport.dto.TripFeeSaveRequest;
import com.yunshu.modules.transport.dto.TripVehicleRequest;
import com.yunshu.modules.transport.service.TripDepartService;
import com.yunshu.modules.transport.service.TripFeeService;
import com.yunshu.modules.transport.service.TripGrossCheckService;
import com.yunshu.modules.transport.service.TripLogQueryService;
import com.yunshu.modules.transport.service.TripPrintService;
import com.yunshu.modules.transport.vo.FeePreviewVO;
import com.yunshu.modules.transport.vo.GrossCheckVO;
import com.yunshu.modules.transport.vo.TripActionVO;
import com.yunshu.modules.transport.vo.TripLogVO;
import com.yunshu.modules.transport.vo.TripPrintVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
 * 运输管理 · 装车配载（B3）：车辆/费用/发车/取消发车/关闭/打印/日志/毛利对账。
 */
@Tag(name = "运输管理-发车与费用")
@RestController
@RequestMapping("/api/v1/transport")
@RequiredArgsConstructor
public class TransportTripOpsController {

    private final TripDepartService departService;
    private final TripFeeService feeService;
    private final TripPrintService printService;
    private final TripLogQueryService logQueryService;
    private final TripGrossCheckService grossCheckService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "保存车辆/司机信息（发车后锁定）")
    @PutMapping("/trip/{id}/vehicle")
    public ApiResult<Void> saveVehicle(@PathVariable Long id, @RequestBody TripVehicleRequest request) {
        departService.saveVehicle(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "保存成本项 + 全车分摊方式，返回分摊结果")
    @PutMapping("/trip/{id}/fees")
    public ApiResult<FeePreviewVO> saveFees(@PathVariable Long id,
                                            @RequestBody @Valid TripFeeSaveRequest request) {
        return ApiResult.ok(feeService.save(id, request));
    }

    @Operation(summary = "费用分摊预览（items 为 JSON 数组字符串，省略则用已保存费用）")
    @GetMapping("/trip/{id}/fees/preview")
    public ApiResult<FeePreviewVO> previewFees(@PathVariable Long id,
                                               @RequestParam(required = false) Integer allocMethod,
                                               @RequestParam(required = false) String items) throws Exception {
        List<TripFeeSaveRequest.Item> list = null;
        if (StringUtils.hasText(items)) {
            list = objectMapper.readValue(items, new TypeReference<List<TripFeeSaveRequest.Item>>() {
            });
        }
        return ApiResult.ok(feeService.preview(id, allocMethod, list));
    }

    @Operation(summary = "发车：20→30、运单 70→80、子车次 10→20")
    @PostMapping("/trip/{id}/depart")
    public ApiResult<TripActionVO> depart(@PathVariable Long id, @RequestBody @Valid DepartRequest request) {
        return ApiResult.ok(departService.depart(id, request));
    }

    @Operation(summary = "取消发车：30→20、运单 80→70、清空发车时间、子车次回 10")
    @PostMapping("/trip/{id}/cancel-depart")
    public ApiResult<TripActionVO> cancelDepart(@PathVariable Long id,
                                                @RequestBody @Valid ReasonRequest request) {
        return ApiResult.ok(departService.cancelDepart(id, request));
    }

    @Operation(summary = "关闭车次：仅未发车，释放全部配载关系")
    @PostMapping("/trip/{id}/close")
    public ApiResult<TripActionVO> close(@PathVariable Long id, @RequestBody @Valid ReasonRequest request) {
        return ApiResult.ok(departService.close(id, request));
    }

    @Operation(summary = "打印运输清单数据（可按子车次过滤，不接模板）")
    @GetMapping("/trip/{id}/print")
    public ApiResult<TripPrintVO> print(@PathVariable Long id,
                                        @RequestParam(required = false) Long subTripId) {
        return ApiResult.ok(printService.print(id, subTripId));
    }

    @Operation(summary = "车次操作日志")
    @GetMapping("/trip/{id}/logs")
    public ApiResult<List<TripLogVO>> logs(@PathVariable Long id) {
        return ApiResult.ok(logQueryService.logs(id));
    }

    @Operation(summary = "车次毛利对账：diff≠0 即分摊异常")
    @GetMapping("/trip/{id}/gross-check")
    public ApiResult<GrossCheckVO> grossCheck(@PathVariable Long id) {
        return ApiResult.ok(grossCheckService.check(id));
    }
}
