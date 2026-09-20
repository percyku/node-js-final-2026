package com.percyku.livefit.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.repository.projection.UserCourseBookingRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GET /api/users/courses → data: { credit_remain, credit_usage, course_booking: [...] } */
public record UserCoursesResponse(
        @JsonProperty("credit_remain") long creditRemain,
        @JsonProperty("credit_usage") long creditUsage,
        @JsonProperty("course_booking") List<Booking> courseBooking) {

    public record Booking(
            @JsonProperty("course_id") UUID courseId,
            String name,
            @JsonProperty("start_at") Instant startAt,
            @JsonProperty("end_at") Instant endAt,
            @JsonProperty("meeting_url") String meetingUrl,
            @JsonProperty("coach_name") String coachName,
            @JsonProperty("cancelled_at") Instant cancelledAt) {

        public static Booking from(UserCourseBookingRow row) {
            return new Booking(row.getCourseId(), row.getName(), row.getStartAt(), row.getEndAt(),
                    row.getMeetingUrl(), row.getCoachName(), row.getCancelledAt());
        }
    }
}
