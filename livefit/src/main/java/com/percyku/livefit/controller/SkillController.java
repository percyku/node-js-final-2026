package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.common.DeleteResult;
import com.percyku.livefit.dto.skill.SkillCreatedResponse;
import com.percyku.livefit.dto.skill.SkillRequest;
import com.percyku.livefit.dto.skill.SkillResponse;
import com.percyku.livefit.service.SkillService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * M1 技能管理，皆免登入。
 * 路徑刻意掛在 /api/coaches/skill，Spring MVC 的字面路徑優先於 /api/coaches/{coachId}，
 * 不會有 Node 版那個「skill 被當成 coachId 吃掉」的順序問題。
 */
@RestController
@RequestMapping("/api/coaches/skill")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ApiResponse<List<SkillResponse>> getSkills() {
        return ApiResponse.success(skillService.getSkills());
    }

    @PostMapping
    public ApiResponse<SkillCreatedResponse> createSkill(@RequestBody(required = false) SkillRequest request) {
        return ApiResponse.success(skillService.createSkill(request));
    }

    @DeleteMapping("/{skillId}")
    public ApiResponse<DeleteResult> deleteSkill(@PathVariable UUID skillId) {
        return ApiResponse.success(skillService.deleteSkill(skillId));
    }
}
