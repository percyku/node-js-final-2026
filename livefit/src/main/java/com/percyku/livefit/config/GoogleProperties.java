package com.percyku.livefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 登入設定，對應環境變數 GOOGLE_CLIENT_ID。
 */
@ConfigurationProperties(prefix = "google")
public class GoogleProperties {

    /** OAuth 2.0 Client ID；留空代表未啟用 Google 登入 */
    private String clientId = "";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
