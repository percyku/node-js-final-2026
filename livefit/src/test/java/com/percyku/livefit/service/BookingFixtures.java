package com.percyku.livefit.service;

import com.percyku.livefit.dto.admin.CoachCourseRequest;
import com.percyku.livefit.dto.admin.CoachSignupRequest;
import com.percyku.livefit.dto.creditpackage.CreditPackageRequest;
import com.percyku.livefit.dto.skill.SkillRequest;
import com.percyku.livefit.dto.user.SignupRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M5 / M6 測試共用的資料建立與清理。
 * 資料都透過 service 建立（走正式的驗證邏輯），並記下 id，由 cleanUp 依外鍵順序刪掉。
 * 測試連的是開發用的 livefit 資料庫，所以每個測試結束都要呼叫 cleanUp。
 */
@TestComponent
class BookingFixtures {

    private static final String PASSWORD = "Aa123456";

    @Autowired
    private UserService userService;
    @Autowired
    private AdminCoachService adminCoachService;
    @Autowired
    private SkillService skillService;
    @Autowired
    private CreditPackageService creditPackageService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> skillIds = new ArrayList<>();
    private final List<UUID> packageIds = new ArrayList<>();
    private final List<UUID> courseIds = new ArrayList<>();

    UUID member() {
        String suffix = UUID.randomUUID().toString();
        UUID id = userService.signup(new SignupRequest(
                "測試會員" + suffix.substring(0, 8), "svc-test-" + suffix + "@example.com", PASSWORD)).user().id();
        userIds.add(id);
        return id;
    }

    /** 回傳教練的 user id（course.user_id 指向的是 User，不是 Coach） */
    UUID coach() {
        UUID userId = member();
        adminCoachService.signupCoach(userId, new CoachSignupRequest(3, "測試用教練", null));
        return userId;
    }

    UUID skill() {
        UUID id = skillService.createSkill(new SkillRequest("測試技能" + UUID.randomUUID())).id();
        skillIds.add(id);
        return id;
    }

    UUID creditPackage(int credits, int price) {
        UUID id = creditPackageService.create(
                new CreditPackageRequest("測試方案" + UUID.randomUUID(), credits, price)).id();
        packageIds.add(id);
        return id;
    }

    /** 一堂一週後開始的課 */
    UUID course(UUID coachUserId, UUID skillId, int maxParticipants) {
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID id = adminCoachService.createCourse(coachUserId, new CoachCourseRequest(
                skillId, "測試課程" + UUID.randomUUID(), "測試用課程",
                start.toString(), start.plus(2, ChronoUnit.HOURS).toString(),
                maxParticipants, "https://example.com/meeting")).course().id();
        courseIds.add(id);
        return id;
    }

    /** 會員買一份方案 */
    void purchase(UUID userId, UUID packageId) {
        creditPackageService.purchase(userId, packageId);
    }

    void cleanUp() {
        // course_booking 沒有外鍵，其餘照 子表 → 主表 的順序刪
        courseIds.forEach(id -> jdbcTemplate.update("DELETE FROM course_booking WHERE course_id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM course_booking WHERE user_id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM credit_purchase WHERE user_id = ?", id));
        courseIds.forEach(id -> jdbcTemplate.update("DELETE FROM course WHERE id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM coaches WHERE user_id = ?", id));
        packageIds.forEach(id -> jdbcTemplate.update("DELETE FROM credit_packages WHERE id = ?", id));
        skillIds.forEach(id -> jdbcTemplate.update("DELETE FROM skills WHERE id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        courseIds.clear();
        userIds.clear();
        packageIds.clear();
        skillIds.clear();
    }
}
