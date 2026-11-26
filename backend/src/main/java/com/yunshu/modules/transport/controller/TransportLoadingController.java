package com.yunshu.modules.transport.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.transport.dto.LoadRequest;
import com.yunshu.modules.transport.dto.LoadedQuery;
import com.yunshu.modules.transport.dto.PoolQuery;
import com.yunshu.modules.transport.dto.SplitRequest;
import com.yunshu.modules.transport.service.TripLoadService;
import com.yunshu.modules.transport.service.TripSplitService;
import com.yunshu.modules.transport.vo.LoadResultVO;
import com.yunshu.modules.transport.vo.LoadedPageVO;
import com.yunshu.modules.transport.vo.PoolPageVO;
import com.yunshu.modules.transport.vo.SplitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运输管理 · 装车配载（B2）：待配载池、已配载列表、配载/移出、拆单预览/拆单装车。
 */
@Tag(name = "运输管理-装车配载")
@RestController
@RequestMapping("/api/v1/transport")
@RequiredArgsConstructor
public class TransportLoadingController {

    private final TripLoadService loadService;
    private final TripSplitService splitService;

    @Operation(summary = "待配载池：一次做足资格校验，不合格单元不出现")
    @GetMapping("/trip/{tripId}/pool")
    public ApiResult<PoolPageVO> pool(@PathVariable Long tripId,
                                      @RequestParam Map<String, String> params) {
        return ApiResult.ok(loadService.pool(tripId, PoolQuery.from(params)));
    }

    @Operation(summary = "已配载列表")
    @GetMapping("/trip/{tripId}/waybills")
    public ApiResult<LoadedPageVO> loaded(@PathVariable Long tripId,
                                          @RequestParam Map<String, String> params) {
        return ApiResult.ok(loadService.loaded(tripId, LoadedQuery.from(params)));
    }

    @Operation(summary = "批量配载：穿梭只落库，重复配载返回失败清单")
    @PostMapping("/trip/{tripId}/waybills")
    public ApiResult<LoadResultVO> load(@PathVariable Long tripId,
                                        @RequestBody @Valid LoadRequest request) {
        return ApiResult.ok(loadService.load(tripId, request));
    }

    @Operation(summary = "批量移出：恢复配载前状态与待配载标记")
    @DeleteMapping("/trip/{tripId}/waybills")
    public ApiResult<LoadResultVO> remove(@PathVariable Long tripId,
                                          @RequestBody @Valid LoadRequest request) {
        return ApiResult.ok(loadService.remove(tripId, request));
    }

    @Operation(summary = "拆单预览：母子件数、金额、重量、体积核对")
    @GetMapping("/trip/{tripId}/split-preview")
    public ApiResult<SplitVO> preview(@PathVariable Long tripId,
                                      @RequestParam Long parentWaybillId,
                                      @RequestParam Integer quantity) {
        return ApiResult.ok(splitService.preview(tripId, parentWaybillId, quantity));
    }

    @Operation(summary = "拆单装车：生成子单并直接配载，母单余额回池")
    @PostMapping("/trip/{tripId}/split-load")
    public ApiResult<SplitVO> splitLoad(@PathVariable Long tripId,
                                        @RequestBody @Valid SplitRequest request) {
        return ApiResult.ok(splitService.splitLoad(tripId, request));
    }
}
