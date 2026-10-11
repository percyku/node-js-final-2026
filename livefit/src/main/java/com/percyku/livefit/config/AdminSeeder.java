package com.percyku.livefit.config;

import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 啟動時確保有一個管理員帳號。沒有任何 API 能產生 ADMIN，這裡是唯一的來源。
 * 寫成 ApplicationRunner 而不是 postgres 的初始化 SQL：資料表是 Hibernate 啟動時才建的，
 * 資料庫容器跑初始化腳本的時候 users 還不存在。
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private static final String ADMIN_NAME = "系統管理員";

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(AdminProperties adminProperties,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.adminProperties = adminProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ValidUtils.isValidString(adminProperties.getEmail())
                || !ValidUtils.isValidString(adminProperties.getPassword())) {
            log.info("未設定 ADMIN_EMAIL 或 ADMIN_PASSWORD，不建立預設管理員");
            return;
        }
        // 登入時 email 會轉小寫再查（UserService.normalizeEmail），這裡要存成同樣的形式
        String email = adminProperties.getEmail().trim().toLowerCase();

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            // 已存在就完全不動：不覆寫改過的密碼，也不把別人註冊的帳號升成管理員
            if (!existing.get().isAdmin()) {
                log.warn("{} 已被一般帳號使用，沒有建立預設管理員", email);
            }
            return;
        }

        User admin = new User();
        admin.setName(ADMIN_NAME);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminProperties.getPassword()));
        admin.setRole(User.ROLE_ADMIN);
        try {
            userRepository.save(admin);
            log.info("已建立預設管理員 {}", email);
        } catch (DataIntegrityViolationException ex) {
            // 另一個實例同時啟動、搶先建好了
            log.info("預設管理員 {} 已由其他實例建立", email);
        }
    }
}
