package com.percyku.livefit.service;

import com.percyku.livefit.dto.admin.CoachRevenueResponse;
import com.percyku.livefit.repository.CoachRepository;
import com.percyku.livefit.repository.CoachWithSkillRepository;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CourseRepository;
import com.percyku.livefit.repository.CreditPackageRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.repository.projection.CreditPackageTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M6 月營收的金額算法：營收 = floor(報名筆數 × 全部方案 Σprice ÷ Σcredit_amount)。
 * 均價取決於資料庫裡「所有」方案，連著真的資料庫沒辦法控制，也不能為了測「沒有方案」把方案刪光，
 * 所以這裡把 repository 換成假的，只驗計算。不需要資料庫。
 */
class CoachRevenueCalculationTest {

    private final UUID coachUserId = UUID.randomUUID();

    private CourseRepository courseRepository;
    private CourseBookingRepository courseBookingRepository;
    private CreditPackageRepository creditPackageRepository;
    private AdminCoachService service;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        courseBookingRepository = mock(CourseBookingRepository.class);
        creditPackageRepository = mock(CreditPackageRepository.class);
        service = new AdminCoachService(mock(UserRepository.class), mock(CoachRepository.class),
                mock(CoachWithSkillRepository.class), courseRepository, courseBookingRepository,
                creditPackageRepository);
        when(courseRepository.existsByUserId(coachUserId)).thenReturn(true);
    }

    @ParameterizedTest(name = "{2} 筆 × ({0} 元 ÷ {1} 堂) = {3}")
    @CsvSource({
            // 均價整除
            "1400, 7, 3, 600",
            // 均價除不盡：333.33… 無條件捨去
            "1000, 3, 1, 333",
            "1000, 3, 2, 666",
            // 先乘再捨去：12.5 × 2 = 25；先把均價捨成 12 再乘會變 24
            "100, 8, 2, 25",
            "1, 2, 1, 0",
            "1, 2, 2, 1",
            // 沒有報名
            "1400, 7, 0, 0",
    })
    void 營收是筆數乘均價後無條件捨去(int totalPrice, int totalCredits, long bookings, long expected) {
        givenPackages(totalPrice, totalCredits);
        givenBookings(bookings, bookings);

        assertThat(revenue().revenue()).isEqualTo(expected);
    }

    /** 先算均價再乘筆數的話，333.3333333333 × 3 = 999.9999999999，捨去後會變 999 */
    @ParameterizedTest(name = "{2} 筆 × ({0} 元 ÷ {1} 堂) = {3}")
    @CsvSource({
            // 均價是循環小數，但乘上筆數後剛好是整數，不能少算 1 元
            "1000, 3, 3, 1000",
            "1000, 3, 6, 2000",
            "100, 7, 7, 100",
            "2000, 6, 3, 1000",
    })
    void 均價除不盡但總額整除時_不能因為小數誤差少1元(int totalPrice, int totalCredits, long bookings, long expected) {
        givenPackages(totalPrice, totalCredits);
        givenBookings(bookings, bookings);

        assertThat(revenue().revenue()).isEqualTo(expected);
    }

    @Test
    void 資料庫裡沒有任何方案_營收是0_不會除以零() {
        givenPackages(0, 0);
        givenBookings(2, 2);

        CoachRevenueResponse.Total total = revenue();

        assertThat(total.revenue()).isZero();
        // 人數與筆數照常回傳
        assertThat(total.participants()).isEqualTo(2);
        assertThat(total.courseCount()).isEqualTo(2);
    }

    @Test
    void 查不到方案總計_營收是0() {
        when(creditPackageRepository.findTotals()).thenReturn(null);
        givenBookings(2, 2);

        assertThat(revenue().revenue()).isZero();
    }

    @Test
    void 金額用報名筆數算_不是用不重複人數算() {
        givenPackages(1400, 7);
        // 兩個人共報了五堂
        givenBookings(2, 5);

        CoachRevenueResponse.Total total = revenue();

        assertThat(total.participants()).isEqualTo(2);
        assertThat(total.courseCount()).isEqualTo(5);
        assertThat(total.revenue()).isEqualTo(1000);
    }

    @Test
    void 金額很大時不會溢位() {
        givenPackages(2_000_000_000, 1);
        givenBookings(1000, 1000);

        assertThat(revenue().revenue()).isEqualTo(2_000_000_000_000L);
    }

    @Test
    void 沒開過課的教練直接回0_不查報名與方案() {
        when(courseRepository.existsByUserId(coachUserId)).thenReturn(false);

        CoachRevenueResponse.Total total = revenue();

        assertThat(total.revenue()).isZero();
        assertThat(total.participants()).isZero();
        assertThat(total.courseCount()).isZero();
        verifyNoInteractions(courseBookingRepository, creditPackageRepository);
    }

    @Test
    void 用UTC的當年與指定的月份去查() {
        givenPackages(1400, 7);
        givenBookings(1, 1);

        service.getRevenue(coachUserId, "march");

        int year = Year.now(ZoneOffset.UTC).getValue();
        verify(courseBookingRepository).countBookingsInMonth(coachUserId, year, 3);
        verify(courseBookingRepository).countDistinctParticipantsInMonth(coachUserId, year, 3);
    }

    // ===== 輔助 =====

    private CoachRevenueResponse.Total revenue() {
        return service.getRevenue(coachUserId, "march").total();
    }

    private void givenBookings(long participants, long bookings) {
        when(courseBookingRepository.countDistinctParticipantsInMonth(eq(coachUserId), anyInt(), anyInt()))
                .thenReturn(participants);
        when(courseBookingRepository.countBookingsInMonth(eq(coachUserId), anyInt(), anyInt()))
                .thenReturn(bookings);
    }

    private void givenPackages(int totalPrice, int totalCredits) {
        when(creditPackageRepository.findTotals()).thenReturn(new CreditPackageTotals() {
            @Override
            public BigDecimal getTotalCreditAmount() {
                return BigDecimal.valueOf(totalCredits);
            }

            @Override
            public BigDecimal getTotalPrice() {
                return BigDecimal.valueOf(totalPrice);
            }
        });
    }
}
