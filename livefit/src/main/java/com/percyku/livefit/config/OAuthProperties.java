package com.percyku.livefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 走 authorization code 流程的第三方登入（GitHub 等）共用的設定，對應環境變數 OAUTH_REDIRECT_URIS。
 */
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    /** 允許前端帶來的 redirect_uri；必須與各平台後台登記的 callback URL 完全相同 */
    private List<String> redirectUris = List.of();

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public boolean isAllowedRedirectUri(String redirectUri) {
        return redirectUri != null && redirectUris.stream()
                .anyMatch(allowed -> allowed.trim().equals(redirectUri));
    }
}
