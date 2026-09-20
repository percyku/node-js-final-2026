package com.percyku.livefit.repository;

import com.percyku.livefit.entity.CourseBooking;
import com.percyku.livefit.repository.projection.CourseParticipantCount;
import com.percyku.livefit.repository.projection.UserCourseBookingRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseBookingRepository extends JpaRepository<CourseBooking, UUID> {

    /**
     * 是否已報名過此課程。
     * 比照 Node 版：不排除已取消的紀錄，因此取消後無法重新報名（openapi 亦如此規範）。
     */
    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);

    Optional<CourseBooking> findByUserIdAndCourseIdAndCancelledAtIsNull(UUID userId, UUID courseId);

    /** 使用者已使用堂數 */
    long countByUserIdAndCancelledAtIsNull(UUID userId);

    /** 課程目前報名人數 */
    long countByCourseIdAndCancelledAtIsNull(UUID courseId);

    /** 教練課程列表的每堂報名人數 */
    @Query(value = "SELECT course_id AS courseId, COUNT(*) AS participants "
            + "FROM course_booking WHERE course_id IN (:courseIds) AND cancelled_at IS NULL "
            + "GROUP BY course_id", nativeQuery = true)
    List<CourseParticipantCount> countParticipantsByCourseIds(Collection<UUID> courseIds);

    /** GET /api/users/courses：使用者的預約清單（含課程與教練姓名），依開課時間排序 */
    @Query(value = "SELECT cb.course_id AS courseId, c.name AS name, c.start_at AS startAt, "
            + "c.end_at AS endAt, c.meeting_url AS meetingUrl, u.name AS coachName, "
            + "cb.cancelled_at AS cancelledAt "
            + "FROM course_booking cb "
            + "JOIN course c ON c.id = cb.course_id "
            + "JOIN users u ON u.id = c.user_id "
            + "WHERE cb.user_id = :userId "
            + "ORDER BY c.start_at ASC", nativeQuery = true)
    List<UserCourseBookingRow> findUserBookings(UUID userId);

    /** M6：某教練在指定年月的不重複報名人數（以報名建立時間 created_at 判定） */
    @Query(value = "SELECT COUNT(DISTINCT cb.user_id) FROM course_booking cb "
            + "JOIN course c ON c.id = cb.course_id "
            + "WHERE c.user_id = :coachUserId AND cb.cancelled_at IS NULL "
            + "AND EXTRACT(YEAR FROM cb.created_at) = :year "
            + "AND EXTRACT(MONTH FROM cb.created_at) = :month", nativeQuery = true)
    long countDistinctParticipantsInMonth(UUID coachUserId, int year, int month);

    /** M6：某教練在指定年月的報名筆數 */
    @Query(value = "SELECT COUNT(cb.id) FROM course_booking cb "
            + "JOIN course c ON c.id = cb.course_id "
            + "WHERE c.user_id = :coachUserId AND cb.cancelled_at IS NULL "
            + "AND EXTRACT(YEAR FROM cb.created_at) = :year "
            + "AND EXTRACT(MONTH FROM cb.created_at) = :month", nativeQuery = true)
    long countBookingsInMonth(UUID coachUserId, int year, int month);
}
