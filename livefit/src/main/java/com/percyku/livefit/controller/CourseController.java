package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.course.CoursePublicResponse;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** M4 課程列表（免登入）＋ M5 報名／取消（需登入） */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<List<CoursePublicResponse>> getCourses() {
        return ApiResponse.success(courseService.getOngoingCourses());
    }

    @PostMapping("/{courseId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Object> book(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable UUID courseId) {
        courseService.book(authUser.getId(), courseId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Object> cancel(@AuthenticationPrincipal AuthUser authUser,
                                      @PathVariable UUID courseId) {
        courseService.cancelBooking(authUser.getId(), courseId);
        return ApiResponse.success(null);
    }
}
