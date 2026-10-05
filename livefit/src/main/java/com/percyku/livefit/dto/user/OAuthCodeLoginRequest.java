package com.percyku.livefit.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 走 authorization code 流程的第三方登入（GitHub 等）共用的請求。
 * code 是平台授權後帶回前端的一次性授權碼；redirect_uri 必須與前端導向授權頁時用的相同。
 */
public record OAuthCodeLoginRequest(
        String code,
        @JsonProperty("redirect_uri") String redirectUri) {
}
