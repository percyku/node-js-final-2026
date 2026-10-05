package com.percyku.livefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub 登入設定，對應環境變數 GITHUB_CLIENT_ID、GITHUB_CLIENT_SECRET。
 */
@ConfigurationProperties(prefix = "github")
public class GithubProperties {

    /** OAuth App 的 Client ID；留空代表未啟用 GitHub 登入 */
    private String clientId = "";

    /** OAuth App 的 Client secret，只在後端用 code 換 access token 時使用 */
    private String clientSecret = "";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
