package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/admin/coaches/{userId} 的 request body */
public record CoachSignupRequest(
        @JsonProperty("experience_years") Integer experienceYears,
        String description,
        @JsonProperty("profile_image_url") String profileImageUrl) {
}
