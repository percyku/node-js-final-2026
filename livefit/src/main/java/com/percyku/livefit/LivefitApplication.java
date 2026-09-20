package com.percyku.livefit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// 驗證一律走 JWT，排除預設的 UserDetailsService（否則啟動時會印出用不到的隨機密碼）
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LivefitApplication {

	public static void main(String[] args) {
		SpringApplication.run(LivefitApplication.class, args);
	}

}
