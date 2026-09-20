package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.dto.coach.CoachDetailResponse;
import com.percyku.livefit.dto.coach.CoachListItem;
import com.percyku.livefit.dto.course.CoursePublicResponse;
import com.percyku.livefit.entity.Coach;
import com.percyku.livefit.repository.CoachRepository;
import com.percyku.livefit.repository.CoachWithSkillRepository;
import com.percyku.livefit.repository.CourseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/coaches.js 的業務邏輯（M4 公開瀏覽） */
@Service
public class CoachService {

    private final CoachRepository coachRepository;
    private final CoachWithSkillRepository coachWithSkillRepository;
    private final CourseRepository courseRepository;

    public CoachService(CoachRepository coachRepository,
                        CoachWithSkillRepository coachWithSkillRepository,
                        CourseRepository courseRepository) {
        this.coachRepository = coachRepository;
        this.coachWithSkillRepository = coachWithSkillRepository;
        this.courseRepository = courseRepository;
    }

    /** per / page 兩個 query 參數在 Node 版都是必填字串，非數字一律 400 */
    @Transactional(readOnly = true)
    public List<CoachListItem> getCoaches(String per, String page) {
        int perInt = parsePositiveInt(per);
        int pageInt = parsePositiveInt(page);

        return coachRepository.findAllWithUser(PageRequest.of(pageInt - 1, perInt))
                .stream()
                .map(CoachListItem::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoachDetailResponse getCoachDetail(UUID coachId) {
        Coach coach = coachRepository.findByIdWithUser(coachId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.COACH_NOT_FOUND));

        List<String> skills = coachWithSkillRepository.findByCoachIdWithSkill(coach.getId())
                .stream()
                .map(cws -> cws.getSkill().getName())
                .toList();

        return new CoachDetailResponse(
                new CoachDetailResponse.CoachUser(coach.getUser().getName(), coach.getUser().getRole()),
                new CoachDetailResponse.CoachInfo(
                        coach.getId(),
                        coach.getUserId(),
                        coach.getExperienceYears(),
                        coach.getDescription(),
                        coach.getProfileImageUrl(),
                        coach.getCreatedAt(),
                        coach.getUpdatedAt(),
                        skills));
    }

    /** 教練的未結束課程 */
    @Transactional(readOnly = true)
    public List<CoursePublicResponse> getCoachCourses(UUID coachId) {
        Coach coach = coachRepository.findByIdWithUser(coachId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.COACH_NOT_FOUND));

        String coachName = coach.getUser().getName();
        return courseRepository.findNotEndedByUserId(coach.getUserId(), Instant.now())
                .stream()
                .map(course -> CoursePublicResponse.of(course, coachName))
                .toList();
    }

    private int parsePositiveInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
    }
}
