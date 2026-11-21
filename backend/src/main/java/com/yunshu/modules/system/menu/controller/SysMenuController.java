package com.yunshu.modules.system.menu.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.system.menu.dto.MenuSaveRequest;
import com.yunshu.modules.system.menu.service.SysMenuService;
import com.yunshu.modules.system.menu.vo.MenuVO;
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

/**
 * 菜单管理接口，契约见方案 10.7。前端 /system/menu 页面直接消费。
 */
@Tag(name = "系统管理-菜单")
@RestController
@RequestMapping("/api/v1/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "查询全量菜单树（含按钮、禁用与隐藏节点）；支持同级服务端排序")
    @GetMapping("/tree")
    public ApiResult<List<MenuVO>> tree(
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String filterModes) {
        return ApiResult.ok(menuService.tree(sortBy, sortOrder));
    }

    @Operation(summary = "查询菜单详情")
    @GetMapping("/{id}")
    public ApiResult<MenuVO> detail(@PathVariable Long id) {
        return ApiResult.ok(menuService.detail(id));
    }

    @Operation(summary = "新增目录/菜单/按钮")
    @PostMapping
    public ApiResult<Long> create(@RequestBody @Valid MenuSaveRequest request) {
        return ApiResult.ok(menuService.create(request));
    }

    @Operation(summary = "编辑菜单节点")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid MenuSaveRequest request) {
        menuService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "删除菜单节点（存在下级内容时拒绝）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "同级排序")
    @PutMapping("/sort")
    public ApiResult<Void> sort(@RequestBody MenuSaveRequest request) {
        menuService.sort(request.getIds());
        return ApiResult.ok();
    }
}
