package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.Course;

import java.time.Instant;
import java.util.UUID;

/** GET /api/admin/coaches/courses/{courseId} 的回應 */
public record CoachCourseDetailResponse(
        UUID id,
        String name,
        String description,
        @JsonProperty("start_at") Instant startAt,
        @JsonProperty("end_at") Instant endAt,
        @JsonProperty("max_participants") Integer maxParticipants,
        @JsonProperty("skill_name") String skillName,
        @JsonProperty("skill_id") UUID skillId,
        @JsonProperty("meeting_url") String meetingUrl) {

    public static CoachCourseDetailResponse from(Course course) {
        return new CoachCourseDetailResponse(
                course.getId(), course.getName(), course.getDescription(),
                course.getStartAt(), course.getEndAt(), course.getMaxParticipants(),
                course.getSkill().getName(), course.getSkillId(), course.getMeetingUrl());
    }
}
