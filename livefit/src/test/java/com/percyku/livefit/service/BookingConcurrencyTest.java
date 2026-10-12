package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.repository.CourseBookingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同時送出的報名。book 是「先查再寫」，兩個請求同時通過檢查的話就會超賣，
 * 這種情況一支一支打的黑箱測試重現不了，所以用多條執行緒直接呼叫 service。
 * 每個案例重複幾次：競爭不是每次都會發生，只跑一次可能剛好通過。
 * 需要 livefit 資料庫在線。
 *
 * 這三個案例在 book 還沒上鎖時每次都失敗（名額 1 的課進了多人、同一人同一堂課寫進 8 筆、
 * 剩餘堂數變成負的）。course_booking 的 (user_id, course_id) 唯一約束只擋得住同一人同一堂課的重複，
 * 名額與堂數仍然只靠 book 裡的鎖，拿掉鎖或改鎖的順序前先跑這裡。
 */
@SpringBootTest
@Import(BookingFixtures.class)
class BookingConcurrencyTest {

    private static final int THREADS = 8;
    private static final int REPEAT = 5;

    @Autowired
    private BookingFixtures fixtures;
    @Autowired
    private CourseService courseService;
    @Autowired
    private UserService userService;
    @Autowired
    private CourseBookingRepository courseBookingRepository;

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    @RepeatedTest(REPEAT)
    void 名額只剩一個_多人同時報名只有一人成功() throws Exception {
        UUID courseId = fixtures.course(fixtures.coach(), fixtures.skill(), 1);
        UUID packageId = fixtures.creditPackage(1, 300);
        List<Callable<String>> attempts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            UUID memberId = fixtures.member();
            fixtures.purchase(memberId, packageId);
            attempts.add(() -> book(memberId, courseId));
        }

        List<String> results = runTogether(attempts);

        assertThat(courseBookingRepository.countByCourseIdAndCancelledAtIsNull(courseId))
                .as("資料庫裡未取消的報名數不能超過名額")
                .isEqualTo(1);
        assertThat(results).filteredOn("成功"::equals).hasSize(1);
        assertThat(results).filteredOn(result -> !"成功".equals(result))
                .containsOnly(ErrorMessages.FIXED_FULL);
    }

    @RepeatedTest(REPEAT)
    void 同一個人同時對同一堂課送出多次報名_只會有一筆() throws Exception {
        UUID courseId = fixtures.course(fixtures.coach(), fixtures.skill(), 10);
        UUID memberId = fixtures.member();
        // 堂數與名額都夠，唯一該擋下來的理由是「已經報名過」
        fixtures.purchase(memberId, fixtures.creditPackage(THREADS, 300));
        List<Callable<String>> attempts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            attempts.add(() -> book(memberId, courseId));
        }

        List<String> results = runTogether(attempts);

        assertThat(courseBookingRepository.countByCourseIdAndCancelledAtIsNull(courseId))
                .as("同一個人對同一堂課只能有一筆報名")
                .isEqualTo(1);
        assertThat(results).filteredOn("成功"::equals).hasSize(1);
        assertThat(results).filteredOn(result -> !"成功".equals(result))
                .containsOnly(ErrorMessages.FIXED_ALREADY_BOOKED);
    }

    @RepeatedTest(REPEAT)
    void 只剩一堂_同時報名多堂不同的課只有一堂成功() throws Exception {
        UUID coachUserId = fixtures.coach();
        UUID skillId = fixtures.skill();
        UUID memberId = fixtures.member();
        fixtures.purchase(memberId, fixtures.creditPackage(1, 300));
        List<Callable<String>> attempts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            UUID courseId = fixtures.course(coachUserId, skillId, 10);
            attempts.add(() -> book(memberId, courseId));
        }

        List<String> results = runTogether(attempts);

        assertThat(userService.getBookings(memberId).creditRemain())
                .as("剩餘堂數不能變成負的")
                .isEqualTo(0);
        assertThat(results).filteredOn("成功"::equals).hasSize(1);
        assertThat(results).filteredOn(result -> !"成功".equals(result))
                .containsOnly(ErrorMessages.FIXED_NO_CREDIT);
    }

    // ===== 輔助 =====

    /** 回傳「成功」或被擋下時的錯誤訊息；其他例外直接往外丟讓測試失敗 */
    private String book(UUID memberId, UUID courseId) {
        try {
            courseService.book(memberId, courseId);
            return "成功";
        } catch (ApiException ex) {
            return ex.getMessage();
        }
    }

    /** 所有執行緒就位後才同時放行，盡量讓它們撞在一起 */
    private List<String> runTogether(List<Callable<String>> attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        CountDownLatch ready = new CountDownLatch(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> attempt : attempts) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return attempt.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
