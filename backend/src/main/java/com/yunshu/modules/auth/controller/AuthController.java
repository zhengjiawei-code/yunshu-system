package com.yunshu.modules.auth.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.auth.service.AuthService;
import com.yunshu.modules.auth.service.CurrentUserResolver;
import com.yunshu.modules.auth.service.TokenStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 真实登录：BCrypt 校验 + 服务端 token（8 小时）；/info 按真实角色与直授计算菜单权限。
 * 顶栏：机构切换（会话内 currentOrgId）+ 个人中心（资料/密码）。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenStore tokenStore;
    private final CurrentUserResolver currentUserResolver;

    @Data
    public static class LoginRequest {
        @NotBlank(message = "请输入账号")
        private String username;
        @NotBlank(message = "请输入密码")
        private String password;
    }

    @Data
    public static class SwitchOrgRequest {
        @NotNull(message = "请选择要切换的机构")
        private Long orgId;
    }

    @Data
    public static class ProfileRequest {
        @NotBlank(message = "请输入真实姓名")
        @Size(min = 2, max = 20, message = "真实姓名长度需为 2-20 个字符")
        private String realName;
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        private String phone;
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class PasswordRequest {
        @NotBlank(message = "请输入原密码")
        private String oldPassword;
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 20, message = "新密码长度需为 6-20 个字符")
        private String newPassword;
        @NotBlank(message = "请再次输入新密码")
        private String confirmPassword;
    }

    @Operation(summary = "登录：账号密码校验通过后签发服务端 token")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody @Valid LoginRequest request) {
        return ApiResult.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    @Operation(summary = "当前用户信息、权限与菜单树；无 token 或已过期返回 HTTP 401")
    @GetMapping("/info")
    public ResponseEntity<ApiResult<Map<String, Object>>> info(HttpServletRequest request) {
        Long userId = currentUserResolver.id(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.fail(401, "登录已失效，请重新登录"));
        }
        return ResponseEntity.ok(ApiResult.ok(authService.info(userId, currentUserResolver.bearer(request))));
    }

    @Operation(summary = "退出登录：销毁服务端 token")
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        tokenStore.revoke(currentUserResolver.bearer(request));
        return ApiResult.ok();
    }

    @Operation(summary = "当前机构与可切换机构列表")
    @GetMapping("/current-org")
    public ApiResult<Map<String, Object>> currentOrg(HttpServletRequest request) {
        return ApiResult.ok(authService.currentOrg(requireUserId(request), currentUserResolver.bearer(request)));
    }

    @Operation(summary = "切换当前机构：仅主机构或已授权机构，越界拒绝")
    @PutMapping("/current-org")
    public ApiResult<Map<String, Object>> switchOrg(HttpServletRequest request,
                                                    @RequestBody @Valid SwitchOrgRequest body) {
        return ApiResult.ok(authService.switchOrg(requireUserId(request),
                currentUserResolver.bearer(request), body.getOrgId()));
    }

    @Operation(summary = "个人资料")
    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> profile(HttpServletRequest request) {
        return ApiResult.ok(authService.profile(requireUserId(request)));
    }

    @Operation(summary = "修改个人资料（姓名/手机/邮箱）")
    @PutMapping("/profile")
    public ApiResult<Void> updateProfile(HttpServletRequest request, @RequestBody @Valid ProfileRequest body) {
        authService.updateProfile(requireUserId(request), body.getRealName(), body.getPhone(), body.getEmail());
        return ApiResult.ok();
    }

    @Operation(summary = "修改密码：成功后注销全部会话，需重新登录")
    @PutMapping("/password")
    public ApiResult<Void> updatePassword(HttpServletRequest request, @RequestBody @Valid PasswordRequest body) {
        authService.updatePassword(requireUserId(request), body.getOldPassword(),
                body.getNewPassword(), body.getConfirmPassword());
        return ApiResult.ok();
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = currentUserResolver.id(request);
        if (userId == null) {
            throw new com.yunshu.common.exception.BizException(401, "登录已失效，请重新登录");
        }
        return userId;
    }
}
