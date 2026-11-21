package com.yunshu.modules.system.dept.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.system.dept.dto.DeptQuery;
import com.yunshu.modules.system.dept.dto.DeptSaveRequest;
import com.yunshu.modules.system.dept.service.SysDeptService;
import com.yunshu.modules.system.dept.vo.DeptPageVO;
import com.yunshu.modules.system.dept.vo.DeptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 机构管理接口，前端 /system/org 页面直接消费。
 */
@Tag(name = "系统管理-机构")
@RestController
@RequestMapping("/api/v1/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @Operation(summary = "查询机构树（全量，含禁用）")
    @GetMapping("/tree")
    public ApiResult<List<DeptVO>> tree() {
        return ApiResult.ok(deptService.tree());
    }

    @Operation(summary = "分页查询机构平铺列表（服务端筛选、分页与合计）")
    @GetMapping("/page")
    public ApiResult<DeptPageVO> page(DeptQuery query) {
        return ApiResult.ok(deptService.page(query));
    }

    @Operation(summary = "查询机构详情")
    @GetMapping("/{id}")
    public ApiResult<DeptVO> detail(@PathVariable Long id) {
        return ApiResult.ok(deptService.detail(id));
    }

    @Operation(summary = "新增机构（总部不可新增）")
    @PostMapping
    public ApiResult<Long> create(@RequestBody @Valid DeptSaveRequest request) {
        return ApiResult.ok(deptService.create(request));
    }

    @Operation(summary = "编辑机构（编码不可改，类型改动需两段校验）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid DeptSaveRequest request) {
        deptService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "删除机构（存在子机构或用户引用时拒绝）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "精简机构树，供选择器使用")
    @GetMapping("/options")
    public ApiResult<List<DeptVO>> options(
            @Parameter(description = "机构类型过滤：1总部 2公司 3部门 4分拨中心 5网点")
            @RequestParam(required = false) Integer type) {
        return ApiResult.ok(deptService.options(type));
    }

    @Operation(summary = "同级排序（二期拖拽预留）")
    @PutMapping("/sort")
    public ApiResult<Void> sort(@RequestBody DeptSaveRequest request) {
        deptService.sort(request.getIds());
        return ApiResult.ok();
    }
}
