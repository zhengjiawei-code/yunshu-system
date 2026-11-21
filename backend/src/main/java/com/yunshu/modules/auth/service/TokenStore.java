package com.yunshu.modules.auth.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 token 会话：内存存储，8 小时过期。
 * 本期不做 JWT；重启后需重新登录。权限不进会话，每次按库实时计算。
 */
@Component
public class TokenStore {

    private static final long TTL_MILLIS = 8 * 60 * 60 * 1000L;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = "yunshu-" + UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(userId, System.currentTimeMillis() + TTL_MILLIS));
        return token;
    }

    public Long resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt() < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return session.userId();
    }

    public void revoke(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token);
        }
    }

    private record Session(Long userId, long expiresAt) {
    }
}
