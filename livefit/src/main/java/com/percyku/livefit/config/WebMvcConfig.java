package com.percyku.livefit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 開啟尾斜線比對，讓 /api/coaches 與 /api/coaches/ 都能對到同一支 handler。
 *
 * Spring Framework 6（Boot 3）起改用 PathPatternParser，預設**不再**把尾斜線視為等價，
 * 但 Node 版用的 Express 預設兩者皆可，前端也依賴這個行為──
 * 例如 frontend/src/api/coaches.js 的教練列表打的是 `coaches/?per=&page=`（帶尾斜線），
 * 不開這個設定的話該頁會整頁 404。
 *
 * 注意：setUseTrailingSlashMatch 在 Spring 6 已標記 deprecated，預計於 Spring 7 移除。
 * 日後若升級到 Spring Boot 4，要改成在各 @RequestMapping 明列 {"", "/"} 兩種 pattern，
 * 或在反向代理層做 301 轉址。
 */
@Configuration
@SuppressWarnings("deprecation")
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(@NonNull PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }
}
