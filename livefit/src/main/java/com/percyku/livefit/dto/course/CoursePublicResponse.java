package com.percyku.livefit.dto.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.Course;

import java.time.Instant;
import java.util.UUID;

/**
 * 公開課程清單的單筆資料，供
 * GET /api/courses 與 GET /api/coaches/{coachId}/courses 共用。
 */
public record CoursePublicResponse(
        UUID id,
        String name,
        String description,
        @JsonProperty("start_at") Instant startAt,
        @JsonProperty("end_at") Instant endAt,
        @JsonProperty("max_participants") Integer maxParticipants,
        @JsonProperty("coach_name") String coachName,
        @JsonProperty("skill_name") String skillName) {

    public static CoursePublicResponse of(Course course, String coachName) {
        return new CoursePublicResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getStartAt(),
                course.getEndAt(),
                course.getMaxParticipants(),
                coachName,
                course.getSkill().getName());
    }
}
