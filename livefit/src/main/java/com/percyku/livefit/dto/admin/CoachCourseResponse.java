package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.Course;

import java.time.Instant;
import java.util.UUID;

/** POST / PUT /api/admin/coaches/courses → data: { course: {...} } */
public record CoachCourseResponse(CourseDetail course) {

    public record CourseDetail(
            UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("skill_id") UUID skillId,
            String name,
            String description,
            @JsonProperty("start_at") Instant startAt,
            @JsonProperty("end_at") Instant endAt,
            @JsonProperty("max_participants") Integer maxParticipants,
            @JsonProperty("meeting_url") String meetingUrl,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt) {
    }

    public static CoachCourseResponse from(Course course) {
        return new CoachCourseResponse(new CourseDetail(
                course.getId(), course.getUserId(), course.getSkillId(), course.getName(),
                course.getDescription(), course.getStartAt(), course.getEndAt(),
                course.getMaxParticipants(), course.getMeetingUrl(),
                course.getCreatedAt(), course.getUpdatedAt()));
    }
}
