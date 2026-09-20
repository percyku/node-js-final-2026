package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** PUT /api/admin/coaches 的 request body */
public record CoachProfileRequest(
        @JsonProperty("experience_years") Integer experienceYears,
        String description,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("skill_ids") List<UUID> skillIds) {
}
