package com.percyku.livefit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已登入但角色不符時的回應。
 * 教練端點比照 Node 版 backend/middlewares/isCoach.js，回 401（而非 403）+「使用者尚未成為教練」。
 * M7 管理員端點是 Node 版沒有的，不必遷就它，回 403 +「需要管理員權限」。
 */
@Component
public class CoachAccessDeniedHandler implements AccessDeniedHandler {

    /** SecurityConfig 裡需要 ADMIN 的端點都在這個路徑底下 */
    private static final String ADMIN_ONLY_PREFIX = "/api/admin/users";

    private final ObjectMapper objectMapper;

    public CoachAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        boolean adminOnly = request.getRequestURI()
                .startsWith(request.getContextPath() + ADMIN_ONLY_PREFIX);
        int status = adminOnly ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED;
        String message = adminOnly ? ErrorMessages.NOT_ADMIN : ErrorMessages.NOT_COACH;

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, message));
    }
}
