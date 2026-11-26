package com.yunshu.modules.export.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.export.dto.ExportTaskCreateRequest;
import com.yunshu.modules.export.service.ExportTaskService;
import com.yunshu.modules.export.vo.ExportTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 异步导出：发起任务、查询进度、下载文件、我的任务列表。
 * 登录态与数据权限沿用数据权限 Web 拦截器写入的上下文，服务层强制任务归属本人。
 */
@Tag(name = "异步导出")
@RestController
@RequestMapping("/api/v1/export/tasks")
@RequiredArgsConstructor
public class ExportTaskController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportTaskService exportTaskService;

    @Operation(summary = "发起导出任务：校验并发与20万行上限，返回 taskId")
    @PostMapping
    public ApiResult<Map<String, Long>> create(@RequestBody ExportTaskCreateRequest request) {
        return ApiResult.ok(Map.of("taskId", exportTaskService.create(request)));
    }

    @Operation(summary = "我的导出任务列表（最近20条，倒序）")
    @GetMapping
    public ApiResult<List<ExportTaskVO>> list() {
        return ApiResult.ok(exportTaskService.recent());
    }

    @Operation(summary = "查询导出任务状态与进度（仅本人）")
    @GetMapping("/{id}")
    public ApiResult<ExportTaskVO> get(@PathVariable Long id) {
        return ApiResult.ok(exportTaskService.get(id));
    }

    @Operation(summary = "下载导出文件：归属/状态/过期校验，中文文件名按 RFC5987 编码")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ExportTaskService.DownloadFile file = exportTaskService.download(id);
        Resource resource = new FileSystemResource(file.path());
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(XLSX)
                .contentLength(file.path().toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export.xlsx\"; filename*=UTF-8''" + encoded)
                .body(resource);
    }
}
