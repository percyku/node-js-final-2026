package com.percyku.livefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 對應 Node 版 backend/config/secret.js 的 JWT_SECRET / JWT_EXPIRES_DAY。
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    /** 支援 30d / 12h / 30m / 3600s / 純秒數，預設等同 Node 版的 "30d" */
    private String expiresDay = "30d";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getExpiresDay() {
        return expiresDay;
    }

    public void setExpiresDay(String expiresDay) {
        this.expiresDay = expiresDay;
    }

    public Duration resolveExpiration() {
        String value = expiresDay == null ? "" : expiresDay.trim();
        if (value.isEmpty()) {
            return Duration.ofDays(30);
        }
        char unit = value.charAt(value.length() - 1);
        if (Character.isDigit(unit)) {
            return Duration.ofSeconds(Long.parseLong(value));
        }
        long amount = Long.parseLong(value.substring(0, value.length() - 1));
        return switch (unit) {
            case 'd', 'D' -> Duration.ofDays(amount);
            case 'h', 'H' -> Duration.ofHours(amount);
            case 'm', 'M' -> Duration.ofMinutes(amount);
            case 's', 'S' -> Duration.ofSeconds(amount);
            default -> throw new IllegalStateException("無法解析 jwt.expires-day：" + expiresDay);
        };
    }
}
