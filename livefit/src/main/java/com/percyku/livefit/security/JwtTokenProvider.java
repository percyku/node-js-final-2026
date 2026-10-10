package com.percyku.livefit.security;

import com.percyku.livefit.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 以 HS256 簽發／驗證 JWT，payload 與 Node 版一致：{ id, role, iat, exp }。
 * openapi 明訂前端會自行 decode 取用 id 與 role，欄位名稱不可更動。
 */
@Component
public class JwtTokenProvider {

    /** HS256 依 RFC 7518 要求金鑰至少 256 bit，即 32 個位元組 */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private SecretKey key;
    private Duration expiration;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.getSecret();
        // 沒有預設值：沒設定就不啟動，避免用一把大家都知道的密鑰簽 token
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "尚未設定 JWT_SECRET。請在 livefit/.env 或環境變數設定，可用 openssl rand -hex 32 產生");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 至少需要 32 個位元組（HS256 規格要求），可用 openssl rand -hex 32 產生");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.expiration = properties.resolveExpiration();
    }

    public String createToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("id", userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 驗證失敗時直接拋出 jjwt 的例外，由 JwtAuthenticationFilter 分類處理 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
