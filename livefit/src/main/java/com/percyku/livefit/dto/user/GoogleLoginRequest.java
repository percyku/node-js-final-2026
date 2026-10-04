package com.percyku.livefit.dto.user;

/** credential 是前端 Google Identity Services 回傳的 ID token */
public record GoogleLoginRequest(String credential) {
}
