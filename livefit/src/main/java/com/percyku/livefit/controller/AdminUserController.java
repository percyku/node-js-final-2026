package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.admin.AdminUserListResponse;
import com.percyku.livefit.dto.admin.CoachSignupRequest;
import com.percyku.livefit.dto.admin.CoachSignupResponse;
import com.percyku.livefit.service.AdminCoachService;
import com.percyku.livefit.service.AdminUserService;
import org.springframework.http.HttpStatus;
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
 * M7 管理員後台，Node 版沒有。兩支都需要 ADMIN 角色（權限在 SecurityConfig 設定）。
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
            @RequestParam(required = false) String role) {
        return ApiResponse.success(adminUserService.listUsers(page, per, role));
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
