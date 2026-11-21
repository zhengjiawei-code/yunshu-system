package com.yunshu.modules.auth.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 token 会话：内存存储，8 小时过期。
 * 本期不做 JWT；重启后需重新登录。权限不进会话，每次按库实时计算。
 * 当前机构（currentOrgId）随会话保存，缺省回落到用户主机构；退出登录/改密销毁会话时一并清除。
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
        Session session = active(token);
        return session == null ? null : session.userId;
    }

    /** 会话中的当前机构；未切换或已过期返回 null，由调用方回落主机构 */
    public Long currentOrgId(String token) {
        Session session = active(token);
        return session == null ? null : session.currentOrgId;
    }

    public void setCurrentOrgId(String token, Long orgId) {
        Session session = active(token);
        if (session != null) {
            session.currentOrgId = orgId;
        }
    }

    public void revoke(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token);
        }
    }

    /** 修改密码等安全场景：注销该用户全部会话，强制重新登录 */
    public void revokeByUser(Long userId) {
        if (userId == null) {
            return;
        }
        sessions.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId));
    }

    private Session active(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    private static final class Session {
        private final Long userId;
        private final long expiresAt;
        private volatile Long currentOrgId;

        private Session(Long userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
