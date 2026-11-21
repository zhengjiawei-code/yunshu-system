package com.yunshu.modules.auth.controller;

import com.yunshu.common.result.ApiResult;
import com.yunshu.modules.auth.service.AuthService;
import com.yunshu.modules.auth.service.CurrentUserResolver;
import com.yunshu.modules.auth.service.TokenStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 真实登录：BCrypt 校验 + 服务端 token（8 小时）；/info 按真实角色与直授计算菜单权限。
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
        return ResponseEntity.ok(ApiResult.ok(authService.info(userId)));
    }

    @Operation(summary = "退出登录：销毁服务端 token")
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        tokenStore.revoke(currentUserResolver.bearer(request));
        return ApiResult.ok();
    }
}
