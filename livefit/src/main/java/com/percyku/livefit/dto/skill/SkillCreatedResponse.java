package com.percyku.livefit.dto.skill;

import com.percyku.livefit.entity.Skill;

import java.time.Instant;
import java.util.UUID;

/** POST /api/coaches/skill 的回應，openapi 的欄位名是 camelCase 的 createdAt */
public record SkillCreatedResponse(UUID id, String name, Instant createdAt) {

    public static SkillCreatedResponse from(Skill skill) {
        return new SkillCreatedResponse(skill.getId(), skill.getName(), skill.getCreatedAt());
    }
}
