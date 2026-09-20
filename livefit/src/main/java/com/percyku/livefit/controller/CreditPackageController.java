package com.percyku.livefit.controller;

import com.percyku.livefit.common.ApiResponse;
import com.percyku.livefit.dto.common.DeleteResult;
import com.percyku.livefit.dto.creditpackage.CreditPackageCreatedResponse;
import com.percyku.livefit.dto.creditpackage.CreditPackageRequest;
import com.percyku.livefit.dto.creditpackage.CreditPackageResponse;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.service.CreditPackageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** M1 方案管理（免登入）＋ M5 購買方案（需登入） */
@RestController
@RequestMapping("/api/credit-package")
public class CreditPackageController {

    private final CreditPackageService creditPackageService;

    public CreditPackageController(CreditPackageService creditPackageService) {
        this.creditPackageService = creditPackageService;
    }

    @GetMapping
    public ApiResponse<List<CreditPackageResponse>> getAll() {
        return ApiResponse.success(creditPackageService.getAll());
    }

    @PostMapping
    public ApiResponse<CreditPackageCreatedResponse> create(
            @RequestBody(required = false) CreditPackageRequest request) {
        return ApiResponse.success(creditPackageService.create(request));
    }

    @DeleteMapping("/{creditPackageId}")
    public ApiResponse<DeleteResult> delete(@PathVariable UUID creditPackageId) {
        return ApiResponse.success(creditPackageService.delete(creditPackageId));
    }

    /** M5：購買方案，需登入 */
    @PostMapping("/{creditPackageId}")
    public ApiResponse<Object> purchase(@AuthenticationPrincipal AuthUser authUser,
                                        @PathVariable UUID creditPackageId) {
        creditPackageService.purchase(authUser.getId(), creditPackageId);
        return ApiResponse.success(null);
    }
}
