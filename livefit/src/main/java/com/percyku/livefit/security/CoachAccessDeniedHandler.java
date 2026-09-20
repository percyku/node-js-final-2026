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
 * 已登入但不是教練時的回應。
 * 比照 Node 版 backend/middlewares/isCoach.js，回 401（而非 403）+「使用者尚未成為教練」。
 */
@Component
public class CoachAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CoachAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(401, ErrorMessages.NOT_COACH));
    }
}
