package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** POST / PUT /api/admin/coaches/courses 的 request body（時間以字串送入） */
public record CoachCourseRequest(
        @JsonProperty("skill_id") UUID skillId,
        String name,
        String description,
        @JsonProperty("start_at") String startAt,
        @JsonProperty("end_at") String endAt,
        @JsonProperty("max_participants") Integer maxParticipants,
        @JsonProperty("meeting_url") String meetingUrl) {
}
