package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.entity.Course;
import com.percyku.livefit.entity.CourseBooking;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CourseRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import com.percyku.livefit.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * book 的檢查都通過、寫入時才被 (user_id, course_id) 唯一約束擋下的情況。
 * 有鎖的時候真的資料庫走不到這裡，所以把 repository 換成假的來模擬。不需要資料庫。
 */
class BookingDuplicateBackstopTest {

    @Test
    void 寫入時被唯一約束擋下_回已經報名過此課程而不是伺服器錯誤() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UserRepository userRepository = mock(UserRepository.class);
        CourseRepository courseRepository = mock(CourseRepository.class);
        CourseBookingRepository courseBookingRepository = mock(CourseBookingRepository.class);
        CreditPurchaseRepository creditPurchaseRepository = mock(CreditPurchaseRepository.class);
        Course course = mock(Course.class);
        when(course.getMaxParticipants()).thenReturn(10);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(mock(User.class)));
        when(courseRepository.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));
        when(courseBookingRepository.existsByUserIdAndCourseId(userId, courseId)).thenReturn(false);
        when(creditPurchaseRepository.sumPurchasedCreditsByUserId(userId)).thenReturn(1L);
        when(courseBookingRepository.saveAndFlush(any(CourseBooking.class)))
                .thenThrow(new DataIntegrityViolationException("uk_course_booking_user_course"));
        CourseService service = new CourseService(
                courseRepository, courseBookingRepository, creditPurchaseRepository, userRepository);

        assertThatThrownBy(() -> service.book(userId, courseId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.FIXED_ALREADY_BOOKED);
                });
    }
}
