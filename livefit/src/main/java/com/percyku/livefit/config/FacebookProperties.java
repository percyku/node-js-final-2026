package com.percyku.livefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facebook 登入設定，對應環境變數 FACEBOOK_APP_ID、FACEBOOK_APP_SECRET。
 */
@ConfigurationProperties(prefix = "facebook")
public class FacebookProperties {

    /** Facebook App 的應用程式編號；留空代表未啟用 Facebook 登入 */
    private String appId = "";

    /** Facebook App 的應用程式密鑰，只在後端用 code 換 access token 時使用 */
    private String appSecret = "";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
}
