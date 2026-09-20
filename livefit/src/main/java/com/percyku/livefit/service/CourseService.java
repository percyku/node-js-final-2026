package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.dto.course.CoursePublicResponse;
import com.percyku.livefit.entity.Course;
import com.percyku.livefit.entity.CourseBooking;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CourseRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/course.js 的業務邏輯（M4 課程列表、M5 報名／取消） */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseBookingRepository courseBookingRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;

    public CourseService(CourseRepository courseRepository,
                         CourseBookingRepository courseBookingRepository,
                         CreditPurchaseRepository creditPurchaseRepository) {
        this.courseRepository = courseRepository;
        this.courseBookingRepository = courseBookingRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
    }

    /** 全站「進行中」課程：start_at <= now AND end_at > now */
    @Transactional(readOnly = true)
    public List<CoursePublicResponse> getOngoingCourses() {
        return courseRepository.findOngoing(Instant.now())
                .stream()
                .map(course -> CoursePublicResponse.of(course, course.getUser().getName()))
                .toList();
    }

    /**
     * 報名課程。四句固定錯誤訊息中有三句在這裡，順序與 Node 版一致：
     * 已報名 → 沒堂數 → 額滿。
     */
    @Transactional
    public void book(UUID userId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.INVALID_ID));

        // 與 Node 版一致：不排除已取消的紀錄，因此取消後無法重新報名
        if (courseBookingRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw ApiException.badRequest(ErrorMessages.FIXED_ALREADY_BOOKED);
        }

        long purchased = creditPurchaseRepository.sumPurchasedCreditsByUserId(userId);
        long used = courseBookingRepository.countByUserIdAndCancelledAtIsNull(userId);
        if (purchased - used <= 0) {
            throw ApiException.badRequest(ErrorMessages.FIXED_NO_CREDIT);
        }

        long booked = courseBookingRepository.countByCourseIdAndCancelledAtIsNull(courseId);
        if (booked >= course.getMaxParticipants()) {
            throw ApiException.badRequest(ErrorMessages.FIXED_FULL);
        }

        courseBookingRepository.save(new CourseBooking(userId, courseId));
    }

    /** 取消報名：軟刪除，只寫入 cancelled_at，堂數自然歸還 */
    @Transactional
    public void cancelBooking(UUID userId, UUID courseId) {
        CourseBooking booking = courseBookingRepository
                .findByUserIdAndCourseIdAndCancelledAtIsNull(userId, courseId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.INVALID_ID));

        booking.setCancelledAt(Instant.now());
        courseBookingRepository.save(booking);
    }
}
