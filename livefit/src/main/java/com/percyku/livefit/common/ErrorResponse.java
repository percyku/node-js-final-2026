package com.percyku.livefit.common;

/**
 * 全域錯誤回應格式：
 * 4xx → {"status":"failed","message":"..."}
 * 5xx → {"status":"error","message":"..."}
 * 對應 Node 版 app.js 的全域錯誤處理中介層。
 */
public record ErrorResponse(String status, String message) {

    public static ErrorResponse of(int httpStatus, String message) {
        return new ErrorResponse(httpStatus >= 500 ? "error" : "failed", message);
    }
}
