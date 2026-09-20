package com.percyku.livefit.dto.coach;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.Coach;

import java.util.UUID;

/** GET /api/coaches 的單筆資料 */
public record CoachListItem(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        String name) {

    public static CoachListItem from(Coach coach) {
        return new CoachListItem(coach.getId(), coach.getUserId(), coach.getUser().getName());
    }
}
