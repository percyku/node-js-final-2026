package com.percyku.livefit.security;

import com.percyku.livefit.config.JwtProperties;
import com.percyku.livefit.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT_SECRET 沒有預設值，沒設定或太短都要讓啟動失敗。不連線、不需要資料庫。 */
class JwtTokenProviderTest {

    @Test
    void 沒設定密鑰時啟動失敗() {
        assertThatThrownBy(() -> providerWith(null).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未設定 JWT_SECRET");
        // application.properties 的 ${JWT_SECRET:} 在沒設定時給的是空字串
        assertThatThrownBy(() -> providerWith("").init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未設定 JWT_SECRET");
    }

    @Test
    void 密鑰少於32個位元組時啟動失敗() {
        assertThatThrownBy(() -> providerWith("a".repeat(31)).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 32 個位元組");
    }

    @Test
    void 密鑰夠長時可以簽發並驗證token() {
        // openssl rand -hex 32 的輸出長度
        JwtTokenProvider provider = providerWith("0123456789abcdef".repeat(4));
        provider.init();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTokenVersion(3);

        Claims claims = provider.parse(provider.createToken(user));

        assertThat(claims.get("id", String.class)).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(User.ROLE_USER);
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(JwtTokenProvider.tokenVersionOf(claims)).isEqualTo(3);
    }

    @Test
    void 沒有ver的舊token視為版本0() {
        assertThat(JwtTokenProvider.tokenVersionOf(Jwts.claims().add("id", "any").build())).isZero();
    }

    private JwtTokenProvider providerWith(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        return new JwtTokenProvider(properties);
    }
}
