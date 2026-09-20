package com.percyku.livefit.dto.skill;

import com.percyku.livefit.entity.Skill;

import java.util.UUID;

/** GET /api/coaches/skill 的單筆資料 */
public record SkillResponse(UUID id, String name) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName());
    }
}
