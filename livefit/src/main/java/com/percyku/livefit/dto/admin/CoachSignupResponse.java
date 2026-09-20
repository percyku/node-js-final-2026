package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.Coach;
import com.percyku.livefit.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * POST /api/admin/coaches/{userId} 的回應。
 * 依 openapi，user 只回 name 與 role——Node 版在這裡會把整個 user entity（含 password hash）吐出來。
 */
public record CoachSignupResponse(SignupUser user, SignupCoach coach) {

    public record SignupUser(String name, String role) {
    }

    public record SignupCoach(
            UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("experience_years") Integer experienceYears,
            String description,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt) {
    }

    public static CoachSignupResponse of(User user, Coach coach) {
        return new CoachSignupResponse(
                new SignupUser(user.getName(), user.getRole()),
                new SignupCoach(coach.getId(), coach.getUserId(), coach.getExperienceYears(),
                        coach.getDescription(), coach.getProfileImageUrl(),
                        coach.getCreatedAt(), coach.getUpdatedAt()));
    }
}
