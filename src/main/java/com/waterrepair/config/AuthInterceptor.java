package com.waterrepair.config;

import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.Roles;
import com.waterrepair.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        AuthService.Session session = authService.authenticate(token);
        request.setAttribute(AuthService.ATTR_USERNAME, session.username());
        request.setAttribute(AuthService.ATTR_ROLE, session.role());

        Roles roles = handlerMethod.getMethodAnnotation(Roles.class);
        if (roles == null) {
            roles = handlerMethod.getBeanType().getAnnotation(Roles.class);
        }
        if (roles != null && Arrays.stream(roles.value()).noneMatch(r -> r.equals(session.role()))) {
            throw ApiException.forbidden("ROLE_FORBIDDEN",
                    "当前角色 " + session.role() + " 无权调用该接口，需要角色 " + String.join("/", roles.value()));
        }
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        String custom = request.getHeader("X-Auth-Token");
        return custom == null ? null : custom.trim();
    }
}
