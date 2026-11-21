package com.yunshu.modules.system.columnconfig.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.auth.service.CurrentUserResolver;
import com.yunshu.modules.system.columnconfig.service.ColumnConfigService;
import com.yunshu.modules.system.columnconfig.vo.ColumnConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 列表列配置接口：统一协议，按「用户 + 页面标识」隔离。
 * 前端不允许把 userId 放进请求，一律取当前登录态，避免越权读写他人配置。
 */
@Tag(name = "系统管理-列配置")
@RestController
@RequestMapping("/api/v1/system/column-configs")
@RequiredArgsConstructor
public class ColumnConfigController {

    private final ColumnConfigService columnConfigService;
    private final CurrentUserResolver currentUserResolver;

    @Data
    public static class SaveRequest {
        @NotBlank(message = "列配置不能为空")
        private String config;
    }

    @Operation(summary = "读取当前用户在某页面的列配置")
    @GetMapping("/{pageKey}")
    public ApiResult<ColumnConfigVO> get(@PathVariable String pageKey, HttpServletRequest request) {
        return ApiResult.ok(columnConfigService.get(currentUserResolver.id(request), pageKey));
    }

    @Operation(summary = "保存列配置（保存后立即生效）")
    @PutMapping("/{pageKey}")
    public ApiResult<ColumnConfigVO> save(@PathVariable String pageKey,
                                          @RequestBody @Valid SaveRequest body,
                                          HttpServletRequest request) {
        return ApiResult.ok(columnConfigService.save(
                currentUserResolver.id(request), pageKey, body.getConfig()));
    }

    @Operation(summary = "恢复默认：删除当前用户在该页面的配置")
    @DeleteMapping("/{pageKey}")
    public ApiResult<Void> reset(@PathVariable String pageKey, HttpServletRequest request) {
        columnConfigService.reset(currentUserResolver.id(request), pageKey);
        return ApiResult.ok();
    }
}
