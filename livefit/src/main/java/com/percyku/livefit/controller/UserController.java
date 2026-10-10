package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.user.CreditPurchaseResponse;
import com.percyku.livefit.dto.user.GoogleLoginRequest;
import com.percyku.livefit.dto.user.LoginRequest;
import com.percyku.livefit.dto.user.LoginResponse;
import com.percyku.livefit.dto.user.OAuthCodeLoginRequest;
import com.percyku.livefit.dto.user.ProfileResponse;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.dto.user.SignupResponse;
import com.percyku.livefit.dto.user.UpdateNameRequest;
import com.percyku.livefit.dto.user.UpdateNameResponse;
import com.percyku.livefit.dto.user.UpdatePasswordRequest;
import com.percyku.livefit.dto.user.UserCoursesResponse;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M2 會員系統 ＋ M5 的兩支個人資料查詢 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@RequestBody(required = false) SignupRequest request) {
        return ApiResponse.success(userService.signup(request));
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @PostMapping("/google")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> googleLogin(@RequestBody(required = false) GoogleLoginRequest request) {
        return ApiResponse.success(userService.googleLogin(request));
    }

    @PostMapping("/github")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> githubLogin(@RequestBody(required = false) OAuthCodeLoginRequest request) {
        return ApiResponse.success(userService.githubLogin(request));
    }

    @PostMapping("/facebook")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> facebookLogin(@RequestBody(required = false) OAuthCodeLoginRequest request) {
        return ApiResponse.success(userService.facebookLogin(request));
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userService.getProfile(authUser.getUser()));
    }

    @PutMapping("/profile")
    public ApiResponse<UpdateNameResponse> updateProfile(@AuthenticationPrincipal AuthUser authUser,
                                                         @RequestBody(required = false) UpdateNameRequest request) {
        return ApiResponse.success(userService.updateName(authUser, request));
    }

    @PutMapping("/password")
    public ApiResponse<Object> updatePassword(@AuthenticationPrincipal AuthUser authUser,
                                              @RequestBody(required = false) UpdatePasswordRequest request) {
        userService.updatePassword(authUser, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/credit-package")
    public ApiResponse<List<CreditPurchaseResponse>> getCreditPurchases(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userService.getCreditPurchases(authUser.getId()));
    }

    @GetMapping("/courses")
    public ApiResponse<UserCoursesResponse> getBookings(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(userService.getBookings(authUser.getId()));
    }
}
