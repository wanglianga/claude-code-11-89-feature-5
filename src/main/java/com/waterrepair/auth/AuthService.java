package com.waterrepair.auth;

import com.waterrepair.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public static final String ATTR_USERNAME = "auth.username";
    public static final String ATTR_ROLE = "auth.role";

    private final UserAccountRepository users;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final long ttlHours;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(UserAccountRepository users,
                       @Value("${app.security.token-ttl-hours:12}") long ttlHours) {
        this.users = users;
        this.ttlHours = ttlHours;
    }

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public Map<String, Object> login(String username, String password) {
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(user.getUsername(), user.getRole(),
                user.getCrewCode(), OffsetDateTime.now().plusHours(ttlHours)));
        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole(),
                "crewCode", user.getCrewCode() == null ? "" : user.getCrewCode(),
                "expiresAt", OffsetDateTime.now().plusHours(ttlHours).toString()
        );
    }

    public Session authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("MISSING_TOKEN", "缺少登录令牌，请先调用 /api/auth/login");
        }
        Session session = sessions.get(token);
        if (session == null) {
            throw ApiException.unauthorized("INVALID_TOKEN", "令牌不存在或服务已重启，请重新登录");
        }
        if (session.expiresAt().isBefore(OffsetDateTime.now())) {
            sessions.remove(token);
            throw ApiException.unauthorized("TOKEN_EXPIRED", "登录已过期，请重新登录");
        }
        return session;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public record Session(String username, String role, String crewCode, OffsetDateTime expiresAt) {
    }
}
