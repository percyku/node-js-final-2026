package com.percyku.livefit.dto.coach;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GET /api/coaches/{coachId} → data: { user: {...}, coach: {...} } */
public record CoachDetailResponse(CoachUser user, CoachInfo coach) {

    public record CoachUser(String name, String role) {
    }

    public record CoachInfo(
            UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("experience_years") Integer experienceYears,
            String description,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            List<String> skills) {
    }
}
