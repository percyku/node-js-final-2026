package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.Coach;

import java.util.List;
import java.util.UUID;

/** GET / PUT /api/admin/coaches 的回應 */
public record CoachProfileResponse(
        UUID id,
        @JsonProperty("experience_years") Integer experienceYears,
        String description,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("skill_ids") List<UUID> skillIds) {

    public static CoachProfileResponse of(Coach coach, List<UUID> skillIds) {
        return new CoachProfileResponse(coach.getId(), coach.getExperienceYears(),
                coach.getDescription(), coach.getProfileImageUrl(), skillIds);
    }
}
