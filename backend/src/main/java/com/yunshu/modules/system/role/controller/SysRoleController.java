package com.yunshu.modules.system.role.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.system.role.dto.RoleMenuRequest;
import com.yunshu.modules.system.role.dto.RoleQuery;
import com.yunshu.modules.system.role.dto.RoleSaveRequest;
import com.yunshu.modules.system.role.service.SysRoleService;
import com.yunshu.modules.system.role.vo.RolePageVO;
import com.yunshu.modules.system.role.vo.RoleVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口，前端 /system/role 页面直接消费。
 * 角色只控制菜单与按钮可见性，不含数据范围；平台配置类接口不受数据权限影响。
 */
@Tag(name = "系统管理-角色")
@RestController
@RequestMapping("/api/v1/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "分页查询角色列表（服务端筛选、排序、分页与合计）")
    @GetMapping("/page")
    public ApiResult<RolePageVO> page(RoleQuery query) {
        return ApiResult.ok(roleService.page(query));
    }

    @Operation(summary = "查询角色详情")
    @GetMapping("/{id}")
    public ApiResult<RoleVO> detail(@PathVariable Long id) {
        return ApiResult.ok(roleService.detail(id));
    }

    @Operation(summary = "新增角色（名称与编码全局唯一）")
    @PostMapping
    public ApiResult<Long> create(@RequestBody @Valid RoleSaveRequest request) {
        return ApiResult.ok(roleService.create(request));
    }

    @Operation(summary = "编辑角色（编码不可改，admin 不可禁用）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody @Valid RoleSaveRequest request) {
        roleService.update(id, request);
        return ApiResult.ok();
    }

    @Operation(summary = "删除角色（admin 与已分配用户的角色拒绝）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "启用角色下拉选项（用户管理绑定角色用）")
    @GetMapping("/options")
    public ApiResult<List<RoleVO>> options() {
        return ApiResult.ok(roleService.options());
    }

    @Operation(summary = "查询角色已授权的菜单ID（admin 返回全量）")
    @GetMapping("/{id}/menus")
    public ApiResult<List<Long>> menuIds(@PathVariable Long id) {
        return ApiResult.ok(roleService.menuIds(id));
    }

    @Operation(summary = "保存角色菜单授权（整体覆盖，admin 拒绝）")
    @PutMapping("/{id}/menus")
    public ApiResult<Void> saveMenus(@PathVariable Long id, @RequestBody RoleMenuRequest request) {
        roleService.saveMenus(id, request.getMenuIds());
        return ApiResult.ok();
    }
}
