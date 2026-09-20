package com.percyku.livefit.security;

import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 對應 Node 版 backend/middlewares/isAuth.js。
 * 驗證失敗不直接回應，而是把訊息放進 request attribute，
 * 交給 JwtAuthenticationEntryPoint 產生與 Node 版一致的 401 內容。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** EntryPoint 會讀這個 attribute 決定 401 的訊息 */
    public static final String AUTH_ERROR_ATTRIBUTE = "livefit.authError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // 沒帶 token：維持匿名，若該路由需要登入會由 EntryPoint 回「請先登入」
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = tokenProvider.parse(token);
            Optional<User> user = resolveUser(claims);
            if (user.isEmpty()) {
                // token 有效但查無使用者
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorMessages.TOKEN_INVALID);
                filterChain.doFilter(request, response);
                return;
            }

            AuthUser authUser = new AuthUser(user.get());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorMessages.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorMessages.TOKEN_INVALID);
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> resolveUser(Claims claims) {
        String id = claims.get("id", String.class);
        if (id == null) {
            return Optional.empty();
        }
        try {
            return userRepository.findById(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
