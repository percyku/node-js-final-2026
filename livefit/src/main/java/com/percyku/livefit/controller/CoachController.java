package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.coach.CoachDetailResponse;
import com.percyku.livefit.dto.coach.CoachListItem;
import com.percyku.livefit.dto.course.CoursePublicResponse;
import com.percyku.livefit.service.CoachService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * M4 公開瀏覽，免登入。
 * 注意 coachId 是 Coach.id，不是 User.id。
 */
@RestController
@RequestMapping("/api/coaches")
public class CoachController {

    private final CoachService coachService;

    public CoachController(CoachService coachService) {
        this.coachService = coachService;
    }

    @GetMapping
    public ApiResponse<List<CoachListItem>> getCoaches(
            @RequestParam(required = false) String per,
            @RequestParam(required = false) String page) {
        return ApiResponse.success(coachService.getCoaches(per, page));
    }

    @GetMapping("/{coachId}")
    public ApiResponse<CoachDetailResponse> getCoachDetail(@PathVariable UUID coachId) {
        return ApiResponse.success(coachService.getCoachDetail(coachId));
    }

    @GetMapping("/{coachId}/courses")
    public ApiResponse<List<CoursePublicResponse>> getCoachCourses(@PathVariable UUID coachId) {
        return ApiResponse.success(coachService.getCoachCourses(coachId));
    }
}
