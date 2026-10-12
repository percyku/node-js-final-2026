package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.dto.admin.CoachRevenueResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M6 月營收「哪些報名算進這個月」。
 * 黑箱測試的報名都發生在執行當下，只測得到「當月」；這裡直接改 course_booking.created_at，
 * 把報名放到指定的時間點，驗月初月底、跨年、去年同月。
 * 只看人數與筆數，金額的算法在 CoachRevenueCalculationTest。
 * 需要 livefit 資料庫在線。
 */
@SpringBootTest
@Import(BookingFixtures.class)
class CoachRevenueMonthBoundaryTest {

    /** 後端以 UTC 的當年計算，測試的時間點都用這一年組出來，不受執行日期影響 */
    private static final int YEAR = Year.now(ZoneOffset.UTC).getValue();

    @Autowired
    private BookingFixtures fixtures;
    @Autowired
    private CourseService courseService;
    @Autowired
    private AdminCoachService adminCoachService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID coachUserId;
    private UUID skillId;
    private UUID courseId;
    private UUID packageId;

    @BeforeEach
    void setUp() {
        coachUserId = fixtures.coach();
        skillId = fixtures.skill();
        courseId = fixtures.course(coachUserId, skillId, 50);
        packageId = fixtures.creditPackage(10, 3000);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    @Test
    void 還沒開課的教練_全部是0() {
        UUID newCoach = fixtures.coach();

        CoachRevenueResponse.Total total = revenue(newCoach, "march");

        assertThat(total.revenue()).isZero();
        assertThat(total.participants()).isZero();
        assertThat(total.courseCount()).isZero();
    }

    @Test
    void 報名算在建立的月份_不是課程上課的月份() {
        // 三月報名、七月上課
        jdbcTemplate.update("UPDATE course SET start_at = CAST(? AS timestamp), end_at = CAST(? AS timestamp) "
                + "WHERE id = ?", YEAR + "-07-10 10:00:00", YEAR + "-07-10 12:00:00", courseId);
        bookAt(courseId, YEAR + "-03-15 10:00:00");

        assertThat(revenue(coachUserId, "march").courseCount()).isEqualTo(1);
        assertThat(revenue(coachUserId, "july").courseCount()).isZero();
    }

    @Test
    void 月初第一秒與月底最後一秒都算在該月() {
        bookAt(courseId, YEAR + "-03-01 00:00:00");
        bookAt(courseId, YEAR + "-03-31 23:59:59");
        bookAt(courseId, YEAR + "-04-01 00:00:00");

        assertThat(revenue(coachUserId, "february").courseCount()).isZero();
        assertThat(revenue(coachUserId, "march").courseCount()).isEqualTo(2);
        assertThat(revenue(coachUserId, "april").courseCount()).isEqualTo(1);
    }

    @Test
    void 只算今年_去年同一個月的報名不計入() {
        bookAt(courseId, (YEAR - 1) + "-03-15 10:00:00");
        bookAt(courseId, YEAR + "-03-15 10:00:00");

        CoachRevenueResponse.Total total = revenue(coachUserId, "march");

        assertThat(total.courseCount()).isEqualTo(1);
        assertThat(total.participants()).isEqualTo(1);
    }

    @Test
    void 跨年的那一秒_去年十二月底不算_今年一月初才算() {
        bookAt(courseId, (YEAR - 1) + "-12-31 23:59:59");
        bookAt(courseId, YEAR + "-01-01 00:00:00");
        bookAt(courseId, YEAR + "-12-31 23:59:59");

        assertThat(revenue(coachUserId, "january").courseCount()).isEqualTo(1);
        // 十二月只有今年底那一筆，去年底的不算
        assertThat(revenue(coachUserId, "december").courseCount()).isEqualTo(1);
    }

    @Test
    void 取消的報名不計入_即使建立時間在該月() {
        bookAt(courseId, YEAR + "-05-10 10:00:00");
        UUID cancelled = bookAt(courseId, YEAR + "-05-11 10:00:00");
        courseService.cancelBooking(cancelled, courseId);

        CoachRevenueResponse.Total total = revenue(coachUserId, "may");

        assertThat(total.courseCount()).isEqualTo(1);
        assertThat(total.participants()).isEqualTo(1);
    }

    @Test
    void 同一個人報兩堂課_人數算1_筆數算2() {
        UUID secondCourse = fixtures.course(coachUserId, skillId, 50);
        UUID memberId = bookAt(courseId, YEAR + "-06-05 10:00:00");
        bookAt(memberId, secondCourse, YEAR + "-06-20 10:00:00");

        CoachRevenueResponse.Total total = revenue(coachUserId, "june");

        assertThat(total.participants()).isEqualTo(1);
        // course_count 的語意是報名筆數，不是課程數
        assertThat(total.courseCount()).isEqualTo(2);
    }

    @Test
    void 只算自己開的課_別的教練的報名不計入() {
        UUID otherCoach = fixtures.coach();
        UUID otherCourse = fixtures.course(otherCoach, skillId, 50);
        bookAt(courseId, YEAR + "-08-10 10:00:00");
        bookAt(otherCourse, YEAR + "-08-10 10:00:00");
        bookAt(otherCourse, YEAR + "-08-11 10:00:00");

        assertThat(revenue(coachUserId, "august").courseCount()).isEqualTo(1);
        assertThat(revenue(otherCoach, "august").courseCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEPTEMBER", "September", "  september  "})
    void 月份名稱不分大小寫_前後空白會去掉(String month) {
        bookAt(courseId, YEAR + "-09-10 10:00:00");

        assertThat(revenue(coachUserId, month).courseCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "9", "09", "sep", "septembre", "九月"})
    void 不是完整的英文月份名一律400(String month) {
        assertThatThrownBy(() -> adminCoachService.getRevenue(coachUserId, month))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.INVALID_FIELDS);
                });
    }

    @Test
    void 沒帶月份回400() {
        assertThatThrownBy(() -> adminCoachService.getRevenue(coachUserId, null))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.INVALID_FIELDS));
    }

    // ===== 輔助 =====

    private CoachRevenueResponse.Total revenue(UUID coachId, String month) {
        return adminCoachService.getRevenue(coachId, month).total();
    }

    /** 建一位有堂數的新會員去報名，再把報名的建立時間改成指定的 UTC 時間。回傳會員 id */
    private UUID bookAt(UUID course, String utcTimestamp) {
        UUID memberId = fixtures.member();
        fixtures.purchase(memberId, packageId);
        bookAt(memberId, course, utcTimestamp);
        return memberId;
    }

    private void bookAt(UUID memberId, UUID course, String utcTimestamp) {
        courseService.book(memberId, course);
        // 欄位是 timestamp without time zone、內容是 UTC；用字串轉型，避開 JDBC 依 JVM 時區換算
        int updated = jdbcTemplate.update(
                "UPDATE course_booking SET created_at = CAST(? AS timestamp) WHERE user_id = ? AND course_id = ?",
                utcTimestamp, memberId, course);
        assertThat(updated).isEqualTo(1);
    }
}
