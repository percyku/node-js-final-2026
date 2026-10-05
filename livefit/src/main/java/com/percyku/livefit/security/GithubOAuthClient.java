package com.percyku.livefit.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.config.GithubProperties;
import com.percyku.livefit.entity.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * 用前端帶來的授權碼向 GitHub 確認使用者身分：code 換 access token，再用 token 取個人資料與 email。
 * GitHub 不像 Google 會給可離線驗簽的 ID token，所以每次登入都要連線三次。
 * 這裡只負責「確認這個 code 真的是 GitHub 發給本專案的」，帳號對應邏輯在 UserService。
 */
@Component
public class GithubOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GithubOAuthClient.class);

    private static final String TOKEN_URI = "https://github.com/login/oauth/access_token";
    private static final String USER_URI = "https://api.github.com/user";
    private static final String EMAILS_URI = "https://api.github.com/user/emails";
    private static final MediaType GITHUB_JSON = MediaType.parseMediaType("application/vnd.github+json");

    // 前端 axios 的逾時是 10 秒，三次呼叫的逾時加起來不能超過，否則前端先斷線、後端卻已建好帳號。
    // 讀取逾時是「等不到資料」的上限，正常情況每次呼叫遠低於此
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    @Autowired
    public GithubOAuthClient(GithubProperties properties, RestClient.Builder builder) {
        this(properties, builder.requestFactory(timeoutRequestFactory()).build());
    }

    GithubOAuthClient(GithubProperties properties, RestClient restClient) {
        this.clientId = properties.getClientId() == null ? "" : properties.getClientId().trim();
        this.clientSecret = properties.getClientSecret() == null ? "" : properties.getClientSecret().trim();
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    public SocialProfile fetchProfile(String code, String redirectUri) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw ApiException.badRequest(ErrorMessages.GITHUB_NOT_CONFIGURED);
        }

        GithubUser user;
        List<GithubEmail> emails;
        try {
            String accessToken = exchangeToken(code, redirectUri);
            user = restClient.get().uri(USER_URI)
                    .headers(headers -> authorize(headers, accessToken))
                    .retrieve()
                    .body(GithubUser.class);
            emails = restClient.get().uri(EMAILS_URI)
                    .headers(headers -> authorize(headers, accessToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException ex) {
            // 連線逾時、GitHub 回 4xx / 5xx、回應格式不符都會到這裡
            log.warn("GitHub 登入驗證失敗：{}", ex.getMessage());
            throw ApiException.badRequest(ErrorMessages.GITHUB_VERIFY_FAILED);
        }

        if (user == null || user.id() == null) {
            log.warn("GitHub 登入驗證失敗：/user 沒有回傳 id");
            throw ApiException.badRequest(ErrorMessages.GITHUB_VERIFY_FAILED);
        }

        // 只採用主要且驗證過的 email，才能安全地拿來綁定既有帳號
        String email = emails == null ? null : emails.stream()
                .filter(candidate -> candidate.primary() && candidate.verified())
                .map(GithubEmail::email)
                .filter(ValidUtils::isValidString)
                .findFirst()
                .orElse(null);
        if (email == null) {
            throw ApiException.badRequest(ErrorMessages.GITHUB_NO_VERIFIED_EMAIL);
        }

        // GitHub 的顯示名稱可以不填，沒填時用帳號名稱
        String name = ValidUtils.isValidString(user.name()) ? user.name() : user.login();
        return new SocialProfile(UserIdentity.PROVIDER_GITHUB, String.valueOf(user.id()), email, true, name);
    }

    private String exchangeToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        TokenResponse response = restClient.post().uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                // 不指定的話 GitHub 會回 form 編碼而不是 JSON
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        // code 無效或過期時 GitHub 回的是 HTTP 200，錯誤放在 body 的 error 欄位，不能只看狀態碼
        if (response == null || !ValidUtils.isValidString(response.accessToken())) {
            log.warn("GitHub 登入驗證失敗：換不到 access token（{}）",
                    response == null ? "回應為空" : response.error());
            throw ApiException.badRequest(ErrorMessages.GITHUB_VERIFY_FAILED);
        }
        return response.accessToken();
    }

    private void authorize(HttpHeaders headers, String accessToken) {
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(GITHUB_JSON));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubUser(Long id, String login, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubEmail(String email, boolean primary, boolean verified) {
    }
}
