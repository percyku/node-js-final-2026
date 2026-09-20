package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.admin.CoachCourseDetailResponse;
import com.percyku.livefit.dto.admin.CoachCourseListItem;
import com.percyku.livefit.dto.admin.CoachCourseRequest;
import com.percyku.livefit.dto.admin.CoachCourseResponse;
import com.percyku.livefit.dto.admin.CoachProfileRequest;
import com.percyku.livefit.dto.admin.CoachProfileResponse;
import com.percyku.livefit.dto.admin.CoachRevenueResponse;
import com.percyku.livefit.dto.admin.CoachSignupRequest;
import com.percyku.livefit.dto.admin.CoachSignupResponse;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.service.AdminCoachService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * M3 教練後台 ＋ M6 月營收。
 * 除了「使用者升級為教練」以外，其餘端點都需要 COACH 角色（權限在 SecurityConfig 設定）。
 *
 * 路由順序提醒：/coaches/courses 是字面路徑，Spring MVC 會優先於 /coaches/{userId} 比對，
 * 因此建立課程不會被升級教練那支吃掉。
 */
@RestController
@RequestMapping("/api/admin/coaches")
public class AdminCoachController {

    private final AdminCoachService adminCoachService;

    public AdminCoachController(AdminCoachService adminCoachService) {
        this.adminCoachService = adminCoachService;
    }

    /** 使用者升級為教練，依 openapi 不需登入 */
    @PostMapping("/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachSignupResponse> signupCoach(
            @PathVariable UUID userId,
            @RequestBody(required = false) CoachSignupRequest request) {
        return ApiResponse.success(adminCoachService.signupCoach(userId, request));
    }

    @GetMapping
    public ApiResponse<CoachProfileResponse> getProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(adminCoachService.getProfile(authUser.getId()));
    }

    @PutMapping
    public ApiResponse<CoachProfileResponse> updateProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody(required = false) CoachProfileRequest request) {
        return ApiResponse.success(adminCoachService.updateProfile(authUser.getId(), request));
    }

    @GetMapping("/courses")
    public ApiResponse<List<CoachCourseListItem>> getCourses(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(adminCoachService.getCourses(authUser.getId()));
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachCourseResponse> createCourse(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody(required = false) CoachCourseRequest request) {
        return ApiResponse.success(adminCoachService.createCourse(authUser.getId(), request));
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<CoachCourseDetailResponse> getCourseDetail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID courseId) {
        return ApiResponse.success(adminCoachService.getCourseDetail(authUser.getId(), courseId));
    }

    @PutMapping("/courses/{courseId}")
    public ApiResponse<CoachCourseResponse> updateCourse(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID courseId,
            @RequestBody(required = false) CoachCourseRequest request) {
        return ApiResponse.success(
                adminCoachService.updateCourse(authUser.getId(), courseId, request));
    }

    @GetMapping("/revenue")
    public ApiResponse<CoachRevenueResponse> getRevenue(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String month) {
        return ApiResponse.success(adminCoachService.getRevenue(authUser.getId(), month));
    }
}
