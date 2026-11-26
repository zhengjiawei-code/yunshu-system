package com.yunshu.modules.transport.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.transport.dto.FeeStatusRequest;
import com.yunshu.modules.transport.dto.FeeTypeSaveRequest;
import com.yunshu.modules.transport.dto.FeeTypeUpdateRequest;
import com.yunshu.modules.transport.dto.GrossFormulaSaveRequest;
import com.yunshu.modules.transport.dto.GrossFormulaTrialRequest;
import com.yunshu.modules.transport.dto.VolumeRatioSaveRequest;
import com.yunshu.modules.transport.service.FeeTypeService;
import com.yunshu.modules.transport.service.GrossFormulaService;
import com.yunshu.modules.transport.service.VolumeRatioService;
import com.yunshu.modules.transport.vo.FeeTypeCatalogVO;
import com.yunshu.modules.transport.vo.FeeTypePageVO;
import com.yunshu.modules.transport.vo.GrossFormulaTrialVO;
import com.yunshu.modules.transport.vo.GrossFormulaVO;
import com.yunshu.modules.transport.vo.VolumeRatioVO;
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

import java.util.List;
import java.util.Map;

/**
 * 系统配置（B5）：费用类型、重泡比、运单毛利公式。
 * 权限码沿用 V34 种子：system:config:fee-type:*、system:config:gross:*、system:route:*。
 */
@Tag(name = "系统配置-费用类型与毛利公式")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class TransportConfigController {

    private final FeeTypeService feeTypeService;
    private final VolumeRatioService volumeRatioService;
    private final GrossFormulaService grossFormulaService;

    @Operation(summary = "费用类型统一列表（收入 4 项只读虚拟行）")
    @GetMapping("/fee-type/page")
    public ApiResult<FeeTypePageVO> feeTypePage(@RequestParam(required = false) List<Integer> feeCategory,
                                                @RequestParam(required = false) String feeCode,
                                                @RequestParam(required = false) String feeName,
                                                @RequestParam(required = false) Boolean enabled,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer pageSize) {
        return ApiResult.ok(feeTypeService.page(feeCategory, feeCode, feeName, enabled, page, pageSize));
    }

    @Operation(summary = "未启用的费用项目录（新增下拉）")
    @GetMapping("/fee-type/catalog")
    public ApiResult<List<FeeTypeCatalogVO>> feeTypeCatalog(@RequestParam(required = false) Integer feeCategory) {
        return ApiResult.ok(feeTypeService.catalog(feeCategory));
    }

    @Operation(summary = "新增费用类型（编码服务端生成）")
    @PostMapping("/fee-type")
    public ApiResult<Map<String, Object>> createFeeType(@RequestBody @Valid FeeTypeSaveRequest request) {
        return ApiResult.ok(feeTypeService.create(request));
    }

    @Operation(summary = "编辑费用类型（名称/编码/类型不可改）")
    @PutMapping("/fee-type/{id}")
    public ApiResult<Void> updateFeeType(@PathVariable Long id,
                                         @RequestBody @Valid FeeTypeUpdateRequest request) {
        feeTypeService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "费用类型启用/停用")
    @PutMapping("/fee-type/{id}/status")
    public ApiResult<Void> changeFeeTypeStatus(@PathVariable Long id,
                                               @RequestBody @Valid FeeStatusRequest request) {
        feeTypeService.changeStatus(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "读取全局重泡比与线路覆盖")
    @GetMapping("/config/volume-weight-ratio")
    public ApiResult<VolumeRatioVO> getVolumeRatio() {
        return ApiResult.ok(volumeRatioService.get());
    }

    @Operation(summary = "保存全局重泡比与线路覆盖（线路为空或>0）")
    @PutMapping("/config/volume-weight-ratio")
    public ApiResult<VolumeRatioVO> saveVolumeRatio(@RequestBody @Valid VolumeRatioSaveRequest request) {
        return ApiResult.ok(volumeRatioService.save(request));
    }

    @Operation(summary = "读取运单毛利公式 + 候选变量 + 变更历史")
    @GetMapping("/config/gross-formula")
    public ApiResult<GrossFormulaVO> getGrossFormula() {
        return ApiResult.ok(grossFormulaService.get());
    }

    @Operation(summary = "保存运单毛利公式（版本自增，history 保留 20 版）")
    @PutMapping("/config/gross-formula")
    public ApiResult<GrossFormulaVO> saveGrossFormula(@RequestBody @Valid GrossFormulaSaveRequest request) {
        return ApiResult.ok(grossFormulaService.save(request));
    }

    @Operation(summary = "单票试算（不落库）")
    @PostMapping("/config/gross-formula/trial")
    public ApiResult<GrossFormulaTrialVO> trialGrossFormula(@RequestBody @Valid GrossFormulaTrialRequest request) {
        return ApiResult.ok(grossFormulaService.trial(request));
    }
}
