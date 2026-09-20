package com.percyku.livefit.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 全域成功回應格式：{"status":"success","data":...}
 * 對應 Node 版 backend 各 controller 的 res.json({status:"success", data})。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(String status, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data);
    }
}
