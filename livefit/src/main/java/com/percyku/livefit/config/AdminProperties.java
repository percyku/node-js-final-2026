package com.percyku.livefit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 預設管理員帳號，對應環境變數 ADMIN_EMAIL、ADMIN_PASSWORD。
 */
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** 任一留空代表啟動時不建立預設管理員 */
    private String email = "";
    private String password = "";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
