package com.yunshu.modules.system.user.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.auth.service.CurrentUserResolver;
import com.yunshu.modules.system.user.dto.UserPermRequest;
import com.yunshu.modules.system.user.dto.UserQuery;
import com.yunshu.modules.system.user.dto.UserSaveRequest;
import com.yunshu.modules.system.user.dto.UserStatusRequest;
import com.yunshu.modules.system.user.service.SysUserService;
import com.yunshu.modules.system.user.vo.UserPageVO;
import com.yunshu.modules.system.user.vo.UserPermVO;
import com.yunshu.modules.system.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * 用户管理接口。平台配置类接口，不受数据权限过滤影响。
 */
@Tag(name = "系统管理-用户")
@RestController
@RequestMapping("/api/v1/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "分页查询用户列表（服务端筛选、排序、分页）")
    @GetMapping("/page")
    public ApiResult<UserPageVO> page(UserQuery query) {
        return ApiResult.ok(userService.page(query));
    }

    @Operation(summary = "查询用户详情（含角色、可登录机构、数据范围）")
    @GetMapping("/{id}")
    public ApiResult<UserVO> detail(@PathVariable Long id) {
        return ApiResult.ok(userService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public ApiResult<Long> create(@RequestBody @Valid UserSaveRequest request,
                                  HttpServletRequest httpRequest) {
        return ApiResult.ok(userService.create(request, currentUserResolver.id(httpRequest)));
    }

    @Operation(summary = "编辑用户（用户名不可修改，admin 不可禁用/改角色）")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id,
                                  @RequestBody @Valid UserSaveRequest request,
                                  HttpServletRequest httpRequest) {
        userService.update(id, request, currentUserResolver.id(httpRequest));
        return ApiResult.ok();
    }

    @Operation(summary = "删除用户（逻辑删除并清理角色/机构授权/直授）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        userService.delete(id, currentUserResolver.id(httpRequest));
        return ApiResult.ok();
    }

    @Operation(summary = "启用/禁用用户（admin 与自身不可禁用）")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id,
                                        @RequestBody @Valid UserStatusRequest request,
                                        HttpServletRequest httpRequest) {
        userService.changeStatus(id, request.getStatus(), currentUserResolver.id(httpRequest));
        return ApiResult.ok();
    }

    @Operation(summary = "重置密码为 123456")
    @PutMapping("/{id}/reset-password")
    public ApiResult<Void> resetPassword(@PathVariable Long id, HttpServletRequest httpRequest) {
        userService.resetPassword(id, currentUserResolver.id(httpRequest));
        return ApiResult.ok();
    }

    @Operation(summary = "查询账号直授（角色已含 / 额外授权 / 显式禁止）")
    @GetMapping("/{id}/perms")
    public ApiResult<UserPermVO> perms(@PathVariable Long id) {
        return ApiResult.ok(userService.perms(id));
    }

    @Operation(summary = "保存账号直授（整体覆盖，禁止优先并级联子孙）")
    @PutMapping("/{id}/perms")
    public ApiResult<Void> savePerms(@PathVariable Long id,
                                     @RequestBody UserPermRequest request,
                                     HttpServletRequest httpRequest) {
        userService.savePerms(id, request.getGrantedIds(), request.getDeniedIds(),
                currentUserResolver.id(httpRequest));
        return ApiResult.ok();
    }
}
