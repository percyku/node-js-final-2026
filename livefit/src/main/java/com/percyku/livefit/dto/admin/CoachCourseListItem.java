package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/** GET /api/admin/coaches/courses 的單筆資料，status 由起訖時間推導 */
public record CoachCourseListItem(
        UUID id,
        String name,
        String status,
        @JsonProperty("start_at") Instant startAt,
        @JsonProperty("end_at") Instant endAt,
        @JsonProperty("max_participants") Integer maxParticipants,
        @JsonProperty("meeting_url") String meetingUrl,
        long participants) {

    public static final String STATUS_NOT_STARTED = "尚未開始";
    public static final String STATUS_ONGOING = "進行中";
    public static final String STATUS_ENDED = "已結束";
}
