package com.percyku.livefit.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/users/profile → data: { user: { name, email, has_password } }
 * has_password 是 Node 版沒有的欄位：前端用它決定修改密碼時要不要顯示舊密碼欄位
 */
public record ProfileResponse(ProfileUser user) {

    public record ProfileUser(String name, String email, @JsonProperty("has_password") boolean hasPassword) {
    }

    public static ProfileResponse of(String name, String email, boolean hasPassword) {
        return new ProfileResponse(new ProfileUser(name, email, hasPassword));
    }
}
