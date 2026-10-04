package com.percyku.livefit.security;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.config.GoogleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 驗證前端 Google Identity Services 取得的 ID token。
 * 以 Google 公開的 JWKS 驗簽（RS256），並檢查 exp、iss、aud（必須是本專案的 Client ID）。
 * 這裡只負責「確認這張 token 真的是 Google 發給本專案的」，帳號對應邏輯在 UserService。
 */
@Component
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleProperties properties) {
        this.clientId = properties.getClientId() == null ? "" : properties.getClientId().trim();

        // 建立 decoder 不會連線，JWKS 在第一次驗證時才下載並快取
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>("iss", iss -> iss != null && ISSUERS.contains(iss)),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clientId))));
        this.decoder = jwtDecoder;
    }

    public GoogleProfile verify(String credential) {
        if (clientId.isEmpty()) {
            throw ApiException.badRequest(ErrorMessages.GOOGLE_NOT_CONFIGURED);
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(credential);
        } catch (JwtException ex) {
            log.warn("Google ID token 驗證失敗：{}", ex.getMessage());
            throw ApiException.badRequest(ErrorMessages.GOOGLE_VERIFY_FAILED);
        }

        return new GoogleProfile(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"));
    }

    public record GoogleProfile(String sub, String email, boolean emailVerified, String name) {
    }
}
