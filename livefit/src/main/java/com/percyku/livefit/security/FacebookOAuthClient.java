package com.percyku.livefit.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.config.FacebookProperties;
import com.percyku.livefit.entity.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 用前端帶來的授權碼向 Facebook 確認使用者身分：code 換 access token，再用 token 取個人資料。
 * 這裡只負責「確認這個 code 真的是 Facebook 發給本專案的」，帳號對應邏輯在 UserService。
 */
@Component
public class FacebookOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(FacebookOAuthClient.class);

    // Graph API 的每個版本約在推出兩年後停用，停用後的請求會被自動導到最舊的可用版本。
    // 前端 config/oauthProviders.js 的授權頁網址用的是同一個版本
    private static final String GRAPH = "https://graph.facebook.com/v26.0";
    private static final String TOKEN_URI = GRAPH + "/oauth/access_token";
    private static final String ME_URI = GRAPH + "/me?fields=id,name,email";

    private final String appId;
    private final String appSecret;
    private final RestClient restClient;

    @Autowired
    public FacebookOAuthClient(FacebookProperties properties, RestClient.Builder builder) {
        this(properties, OAuthRestClients.withTimeouts(builder));
    }

    FacebookOAuthClient(FacebookProperties properties, RestClient restClient) {
        this.appId = properties.getAppId() == null ? "" : properties.getAppId().trim();
        this.appSecret = properties.getAppSecret() == null ? "" : properties.getAppSecret().trim();
        this.restClient = restClient;
    }

    public SocialProfile fetchProfile(String code, String redirectUri) {
        if (appId.isEmpty() || appSecret.isEmpty()) {
            throw ApiException.badRequest(ErrorMessages.FACEBOOK_NOT_CONFIGURED);
        }

        FacebookUser user;
        try {
            // 換 token 是 GET，密鑰在 query string 裡，所以網址不能寫進 log
            // （RestClient 的例外訊息只含狀態碼與回應內容，連線失敗時也會去掉 query）
            TokenResponse token = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(TOKEN_URI)
                            .queryParam("client_id", appId)
                            .queryParam("client_secret", appSecret)
                            // 必須與前端導向授權頁時用的 redirect_uri 完全相同，否則 Facebook 會拒絕
                            .queryParam("redirect_uri", redirectUri)
                            .queryParam("code", code)
                            .build().encode().toUri())
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || !ValidUtils.isValidString(token.accessToken())) {
                log.warn("Facebook 登入驗證失敗：換不到 access token");
                throw ApiException.badRequest(ErrorMessages.FACEBOOK_VERIFY_FAILED);
            }

            user = restClient.get().uri(ME_URI)
                    .headers(headers -> headers.setBearerAuth(token.accessToken()))
                    .retrieve()
                    .body(FacebookUser.class);
        } catch (RestClientException ex) {
            // code 無效或過期（Facebook 回 400）、連線逾時、回應格式不符都會到這裡
            log.warn("Facebook 登入驗證失敗：{}", ex.getMessage());
            throw ApiException.badRequest(ErrorMessages.FACEBOOK_VERIFY_FAILED);
        }

        if (user == null || !ValidUtils.isValidString(user.id())) {
            log.warn("Facebook 登入驗證失敗：/me 沒有回傳 id");
            throw ApiException.badRequest(ErrorMessages.FACEBOOK_VERIFY_FAILED);
        }
        // 用手機號碼註冊、或使用者在授權頁取消勾選 email 時不會有這個欄位
        if (!ValidUtils.isValidString(user.email())) {
            throw ApiException.badRequest(ErrorMessages.FACEBOOK_NO_EMAIL);
        }

        // Facebook 不提供 email 是否驗證過的旗標，一律當成沒驗證過：可以建立新帳號，但不能綁定既有帳號
        return new SocialProfile(UserIdentity.PROVIDER_FACEBOOK, user.id(), user.email(), false, user.name());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FacebookUser(String id, String name, String email) {
    }
}
