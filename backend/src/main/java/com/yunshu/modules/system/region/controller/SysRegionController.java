package com.yunshu.modules.system.region.controller;

import com.yunshu.common.datascope.DataScopeContext;
import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.system.region.service.SysRegionService;
import com.yunshu.modules.system.region.vo.RegionImportCheckVO;
import com.yunshu.modules.system.region.vo.RegionImportResultVO;
import com.yunshu.modules.system.region.vo.RegionNodeVO;
import com.yunshu.modules.system.region.vo.RegionStatsVO;
import com.yunshu.modules.system.region.vo.RegionSyncLogPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "行政区划")
@RestController
@RequestMapping("/api/v1/system/regions")
@RequiredArgsConstructor
public class SysRegionController {

    private final SysRegionService regionService;

    @Operation(summary = "逐级查询区划")
    @GetMapping("/children")
    public ApiResult<List<RegionNodeVO>> children(@RequestParam(required = false) String parentCode) {
        return ApiResult.ok(regionService.children(parentCode));
    }

    @Operation(summary = "按名称搜索区划")
    @GetMapping("/search")
    public ApiResult<List<RegionNodeVO>> search(@RequestParam String keyword,
                                                @RequestParam(required = false) Integer limit) {
        return ApiResult.ok(regionService.search(keyword, limit));
    }

    @Operation(summary = "区划数据统计")
    @GetMapping("/stats")
    public ApiResult<RegionStatsVO> stats() {
        return ApiResult.ok(regionService.stats());
    }

    @Operation(summary = "离线 JSON 导入预检")
    @PostMapping("/import/precheck")
    public ApiResult<RegionImportCheckVO> precheck(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) String version) {
        return ApiResult.ok(regionService.precheck(file, version));
    }

    @Operation(summary = "离线 JSON 幂等导入")
    @PostMapping("/import")
    public ApiResult<RegionImportResultVO> importFile(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(required = false) String version) {
        return ApiResult.ok(regionService.importFile(file, version, currentUserId()));
    }

    @Operation(summary = "在线同步（默认关闭）")
    @PostMapping("/sync-online")
    public ApiResult<RegionImportResultVO> syncOnline() {
        return ApiResult.ok(regionService.syncOnline(currentUserId()));
    }

    @Operation(summary = "同步日志分页")
    @GetMapping("/sync-logs")
    public ApiResult<RegionSyncLogPageVO> logs(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResult.ok(regionService.logs(pageNum, pageSize));
    }

    @Operation(summary = "同步日志详情与差异报告")
    @GetMapping("/sync-logs/{id}")
    public ApiResult<Map<String, Object>> logDetail(@PathVariable Long id) {
        return ApiResult.ok(regionService.logDetail(id));
    }

    private Long currentUserId() {
        DataScopeContext context = DataScopeContext.current();
        return context == null ? null : context.getUserId();
    }
}
