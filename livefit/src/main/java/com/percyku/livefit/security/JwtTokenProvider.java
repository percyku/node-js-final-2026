package com.percyku.livefit.security;

import com.percyku.livefit.config.JwtProperties;
import com.percyku.livefit.entity.User;
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

/**
 * 以 HS256 簽發／驗證 JWT，payload 是 Node 版的 { id, role, iat, exp } 再加上 ver。
 * openapi 明訂前端會自行 decode 取用 id 與 role，欄位名稱不可更動。
 * ver 是簽發當下的 users.token_version，JwtAuthenticationFilter 用它判斷 token 是否已被作廢。
 */
@Component
public class JwtTokenProvider {

    /** HS256 依 RFC 7518 要求金鑰至少 256 bit，即 32 個位元組 */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_VERSION = "ver";

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

    /** 收整個 User 而不是個別欄位，避免呼叫端漏帶 token_version */
    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("id", user.getId().toString())
                .claim("role", user.getRole())
                .claim(CLAIM_VERSION, user.getTokenVersion())
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

    /** 這個功能上線前簽發的 token 沒有 ver，視為 0（與 users.token_version 的預設值相同） */
    public static int tokenVersionOf(Claims claims) {
        Integer version = claims.get(CLAIM_VERSION, Integer.class);
        return version == null ? 0 : version;
    }
}
