package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.admin.AdminCreatedResponse;
import com.percyku.livefit.dto.admin.AdminUserListResponse;
import com.percyku.livefit.dto.admin.CoachSignupRequest;
import com.percyku.livefit.dto.admin.CoachSignupResponse;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.service.AdminCoachService;
import com.percyku.livefit.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * M7 管理員後台，Node 版沒有。全部都需要 ADMIN 角色（權限在 SecurityConfig 設定）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminCoachService adminCoachService;

    public AdminUserController(AdminUserService adminUserService,
                               AdminCoachService adminCoachService) {
        this.adminUserService = adminUserService;
        this.adminCoachService = adminCoachService;
    }

    @GetMapping
    public ApiResponse<AdminUserListResponse> listUsers(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String per,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminUserService.listUsers(page, per, role, keyword));
    }

    /** body 與註冊相同，建立出來的帳號 role 是 ADMIN */
    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCreatedResponse> createAdmin(
            @RequestBody(required = false) SignupRequest request) {
        return ApiResponse.success(adminUserService.createAdmin(request));
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Object> resetAdminPassword(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID userId) {
        adminUserService.resetAdminPassword(authUser, userId);
        return ApiResponse.success(null);
    }

    /** 與 M3 的 POST /api/admin/coaches/{userId} 做同一件事，差別是要 ADMIN 才能呼叫 */
    @PostMapping("/{userId}/coach")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachSignupResponse> promoteToCoach(
            @PathVariable UUID userId,
            @RequestBody(required = false) CoachSignupRequest request) {
        return ApiResponse.success(adminCoachService.promoteByAdmin(userId, request));
    }
}
