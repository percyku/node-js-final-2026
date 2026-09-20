package com.percyku.livefit.common;

/**
 * 可預期的業務錯誤，對應 Node 版 backend/utils/appError.js 的 appError(status, message)。
 * 由 Service 層丟出，GlobalExceptionHandler 統一轉成 {"status":"failed","message":...}。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }
}
