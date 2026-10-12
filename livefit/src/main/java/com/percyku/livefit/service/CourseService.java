package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.dto.course.CoursePublicResponse;
import com.percyku.livefit.entity.Course;
import com.percyku.livefit.entity.CourseBooking;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CourseRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import com.percyku.livefit.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserRepository userRepository;

    public CourseService(CourseRepository courseRepository,
                         CourseBookingRepository courseBookingRepository,
                         CreditPurchaseRepository creditPurchaseRepository,
                         UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.courseBookingRepository = courseBookingRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.userRepository = userRepository;
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
     *
     * 下面三項檢查都是「先查再寫」，同時進來的請求會一起通過檢查而超賣，所以先鎖兩列讓它們排隊：
     * 鎖使用者，同一個人的報名一次只處理一筆（擋重複報名與堂數被扣成負的）；
     * 鎖課程，同一堂課的報名一次只處理一筆（擋超過名額）。
     * 一律先鎖使用者再鎖課程，順序固定才不會互相等待而死結。
     *
     * course_booking 另有 (user_id, course_id) 的唯一約束，是鎖之外的第二道防線。
     */
    @Transactional
    public void book(UUID userId, UUID courseId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.unauthorized(ErrorMessages.TOKEN_INVALID));
        Course course = courseRepository.findByIdForUpdate(courseId)
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

        try {
            // 用 saveAndFlush 讓 INSERT 當下就送出；用 save 的話會拖到交易提交，例外在這裡接不到
            courseBookingRepository.saveAndFlush(new CourseBooking(userId, courseId));
        } catch (DataIntegrityViolationException ex) {
            // 有鎖的情況下不該走到這裡；走到代表上面的檢查被繞過，由唯一約束擋下
            throw ApiException.badRequest(ErrorMessages.FIXED_ALREADY_BOOKED);
        }
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
