package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.entity.CourseBooking;
import com.percyku.livefit.entity.CreditPurchase;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M5 購買與報名寫進資料庫的內容。
 * 根目錄的黑箱測試與 Postman 只能從 API 的回應推測，這裡直接看資料列：
 * 取消是不是真的只寫 cancelled_at、失敗的報名有沒有留下資料、購買紀錄存的是不是當下的快照。
 * 需要 livefit 資料庫在線。
 */
@SpringBootTest
@Import(BookingFixtures.class)
class BookingDatabaseStateTest {

    @Autowired
    private BookingFixtures fixtures;
    @Autowired
    private CourseService courseService;
    @Autowired
    private UserService userService;
    @Autowired
    private CourseBookingRepository courseBookingRepository;
    @Autowired
    private CreditPurchaseRepository creditPurchaseRepository;

    private UUID memberId;
    private UUID packageId;
    private UUID courseId;
    private UUID coachUserId;
    private UUID skillId;

    @BeforeEach
    void setUp() {
        coachUserId = fixtures.coach();
        skillId = fixtures.skill();
        courseId = fixtures.course(coachUserId, skillId, 10);
        packageId = fixtures.creditPackage(3, 1000);
        memberId = fixtures.member();
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    // ===== 購買 =====

    @Test
    void 購買會寫入當下方案的堂數與價格() {
        Instant before = Instant.now();
        fixtures.purchase(memberId, packageId);

        List<CreditPurchase> purchases = purchasesOf(memberId);
        assertThat(purchases).hasSize(1);
        CreditPurchase purchase = purchases.get(0);
        assertThat(purchase.getCreditPackageId()).isEqualTo(packageId);
        assertThat(purchase.getPurchasedCredits()).isEqualTo(3);
        // price_paid 是 numeric(10,2)，方案的 price 是 integer
        assertThat(purchase.getPricePaid()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(purchase.getPurchaseAt()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    @Test
    void 同一個方案買兩次是兩筆紀錄_堂數累加() {
        fixtures.purchase(memberId, packageId);
        fixtures.purchase(memberId, packageId);

        assertThat(purchasesOf(memberId)).hasSize(2);
        assertThat(userService.getBookings(memberId).creditRemain()).isEqualTo(6);
    }

    @Test
    void 購買不存在的方案不會留下紀錄() {
        assertThatThrownBy(() -> fixtures.purchase(memberId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.INVALID_ID));

        assertThat(purchasesOf(memberId)).isEmpty();
    }

    // ===== 報名 =====

    @Test
    void 報名會寫入一筆未取消的紀錄_兩個建立時間都有值() {
        fixtures.purchase(memberId, packageId);
        courseService.book(memberId, courseId);

        List<CourseBooking> bookings = bookingsOf(memberId);
        assertThat(bookings).hasSize(1);
        CourseBooking booking = bookings.get(0);
        assertThat(booking.getCourseId()).isEqualTo(courseId);
        assertThat(booking.getCancelledAt()).isNull();
        // booking_at 與 created_at 都是建立時間，月營收用的是 created_at
        assertThat(booking.getBookingAt()).isNotNull();
        assertThat(booking.getCreatedAt()).isNotNull();
    }

    @Test
    void 剩餘堂數沒有欄位_是購買總堂數減掉未取消的報名數() {
        fixtures.purchase(memberId, packageId);
        UUID secondCourse = fixtures.course(coachUserId, skillId, 10);

        courseService.book(memberId, courseId);
        courseService.book(memberId, secondCourse);

        var bookings = userService.getBookings(memberId);
        assertThat(bookings.creditRemain()).isEqualTo(1);
        assertThat(bookings.creditUsage()).isEqualTo(2);
        // 購買紀錄本身不會被扣
        assertThat(purchasesOf(memberId).get(0).getPurchasedCredits()).isEqualTo(3);
    }

    @Test
    void 沒有堂數時報名失敗_不會留下紀錄() {
        assertThatThrownBy(() -> courseService.book(memberId, courseId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.FIXED_NO_CREDIT));

        assertThat(bookingsOf(memberId)).isEmpty();
    }

    @Test
    void 堂數用完後報名失敗_不會留下紀錄() {
        UUID onePackage = fixtures.creditPackage(1, 300);
        fixtures.purchase(memberId, onePackage);
        UUID secondCourse = fixtures.course(coachUserId, skillId, 10);
        courseService.book(memberId, courseId);

        assertThatThrownBy(() -> courseService.book(memberId, secondCourse))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.FIXED_NO_CREDIT));

        assertThat(bookingsOf(memberId)).hasSize(1);
    }

    @Test
    void 額滿時報名失敗_不會留下紀錄也不扣堂數() {
        UUID smallCourse = fixtures.course(coachUserId, skillId, 1);
        UUID first = fixtures.member();
        fixtures.purchase(first, packageId);
        courseService.book(first, smallCourse);
        fixtures.purchase(memberId, packageId);

        assertThatThrownBy(() -> courseService.book(memberId, smallCourse))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.FIXED_FULL));

        assertThat(bookingsOf(memberId)).isEmpty();
        assertThat(userService.getBookings(memberId).creditRemain()).isEqualTo(3);
    }

    // ===== 取消 =====

    @Test
    void 取消是軟刪除_資料列還在_只多了cancelled_at() {
        fixtures.purchase(memberId, packageId);
        courseService.book(memberId, courseId);
        CourseBooking before = bookingsOf(memberId).get(0);

        Instant cancelStart = Instant.now();
        courseService.cancelBooking(memberId, courseId);

        List<CourseBooking> bookings = bookingsOf(memberId);
        assertThat(bookings).hasSize(1);
        CourseBooking after = bookings.get(0);
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getCancelledAt()).isBetween(cancelStart.minusSeconds(1), Instant.now().plusSeconds(1));
        // 建立時間不會因為取消而改變，否則月營收會把它算到別的月份
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getBookingAt()).isEqualTo(before.getBookingAt());
    }

    @Test
    void 取消後堂數歸還_但取消過的課不能再報名_也不會多一筆() {
        fixtures.purchase(memberId, packageId);
        courseService.book(memberId, courseId);
        courseService.cancelBooking(memberId, courseId);

        assertThat(userService.getBookings(memberId).creditRemain()).isEqualTo(3);
        assertThatThrownBy(() -> courseService.book(memberId, courseId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.FIXED_ALREADY_BOOKED));
        assertThat(bookingsOf(memberId)).hasSize(1);
    }

    @Test
    void 取消後名額釋出_別人可以報名() {
        UUID smallCourse = fixtures.course(coachUserId, skillId, 1);
        fixtures.purchase(memberId, packageId);
        courseService.book(memberId, smallCourse);
        courseService.cancelBooking(memberId, smallCourse);

        UUID other = fixtures.member();
        fixtures.purchase(other, packageId);
        courseService.book(other, smallCourse);

        assertThat(courseBookingRepository.countByCourseIdAndCancelledAtIsNull(smallCourse)).isEqualTo(1);
    }

    @Test
    void 取消第二次失敗_cancelled_at維持第一次的時間() {
        fixtures.purchase(memberId, packageId);
        courseService.book(memberId, courseId);
        courseService.cancelBooking(memberId, courseId);
        Instant firstCancelledAt = bookingsOf(memberId).get(0).getCancelledAt();

        assertThatThrownBy(() -> courseService.cancelBooking(memberId, courseId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.INVALID_ID));

        assertThat(bookingsOf(memberId).get(0).getCancelledAt()).isEqualTo(firstCancelledAt);
    }

    @Test
    void 不能取消別人的報名() {
        fixtures.purchase(memberId, packageId);
        courseService.book(memberId, courseId);
        UUID other = fixtures.member();

        assertThatThrownBy(() -> courseService.cancelBooking(other, courseId))
                .isInstanceOf(ApiException.class);

        assertThat(bookingsOf(memberId).get(0).getCancelledAt()).isNull();
    }

    // ===== 輔助 =====

    private List<CourseBooking> bookingsOf(UUID userId) {
        return courseBookingRepository.findAll().stream()
                .filter(booking -> booking.getUserId().equals(userId))
                .toList();
    }

    private List<CreditPurchase> purchasesOf(UUID userId) {
        return creditPurchaseRepository.findAll().stream()
                .filter(purchase -> purchase.getUserId().equals(userId))
                .toList();
    }
}
