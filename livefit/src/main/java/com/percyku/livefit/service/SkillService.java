package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.common.DeleteResult;
import com.percyku.livefit.dto.skill.SkillCreatedResponse;
import com.percyku.livefit.dto.skill.SkillRequest;
import com.percyku.livefit.dto.skill.SkillResponse;
import com.percyku.livefit.entity.Skill;
import com.percyku.livefit.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/skill.js 的業務邏輯 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> getSkills() {
        return skillRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(SkillResponse::from)
                .toList();
    }

    @Transactional
    public SkillCreatedResponse createSkill(SkillRequest request) {
        String name = request == null ? null : request.name();
        if (!ValidUtils.isValidString(name)) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        String trimmed = name.trim();
        if (skillRepository.findByName(trimmed).isPresent()) {
            throw ApiException.conflict(ErrorMessages.DUPLICATED);
        }

        Skill skill = new Skill();
        skill.setName(trimmed);
        // saveAndFlush：建立時間要等 flush 才會寫入，否則回應的 createdAt 會是 null
        return SkillCreatedResponse.from(skillRepository.saveAndFlush(skill));
    }

    @Transactional
    public DeleteResult deleteSkill(UUID skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw ApiException.badRequest(ErrorMessages.INVALID_ID);
        }
        skillRepository.deleteById(skillId);
        return DeleteResult.of(1);
    }
}
