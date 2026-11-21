package com.yunshu.modules.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从 Authorization: Bearer xxx 解析当前登录用户ID；未登录返回 null。
 * 本期只做登录态，不做接口级按钮鉴权。
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final TokenStore tokenStore;

    public Long id(HttpServletRequest request) {
        return tokenStore.resolve(bearer(request));
    }

    public String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String prefix = "Bearer ";
        return header.startsWith(prefix) ? header.substring(prefix.length()).trim() : header.trim();
    }
}
