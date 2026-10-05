package com.percyku.livefit.security;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.config.GithubProperties;
import com.percyku.livefit.entity.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** GitHub 的回應用 MockRestServiceServer 假造，不會真的連線 */
class GithubOAuthClientTest {

    private static final String TOKEN_URI = "https://github.com/login/oauth/access_token";
    private static final String USER_URI = "https://api.github.com/user";
    private static final String EMAILS_URI = "https://api.github.com/user/emails";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback/github";

    private MockRestServiceServer server;
    private GithubOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubOAuthClient(properties("client-id", "client-secret"), builder.build());
    }

    @Test
    void 成功時回傳主要且驗證過的email() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("code=the-code")))
                .andRespond(withSuccess("""
                        {"access_token":"gho_token","token_type":"bearer","scope":"read:user,user:email"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URI))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_token"))
                .andRespond(withSuccess("""
                        {"id":12345,"login":"octocat","name":"The Octocat","email":null,"public_repos":8}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(EMAILS_URI))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_token"))
                .andRespond(withSuccess("""
                        [{"email":"old@example.com","primary":false,"verified":true,"visibility":null},
                         {"email":"octocat@example.com","primary":true,"verified":true,"visibility":"private"}]
                        """, MediaType.APPLICATION_JSON));

        SocialProfile profile = client.fetchProfile("the-code", REDIRECT_URI);

        assertThat(profile).isEqualTo(new SocialProfile(
                UserIdentity.PROVIDER_GITHUB, "12345", "octocat@example.com", true, "The Octocat"));
        server.verify();
    }

    @Test
    void 沒填顯示名稱時用帳號名稱() {
        respondToken();
        server.expect(requestTo(USER_URI)).andRespond(withSuccess(
                "{\"id\":12345,\"login\":\"octocat\",\"name\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(EMAILS_URI)).andRespond(withSuccess(
                "[{\"email\":\"octocat@example.com\",\"primary\":true,\"verified\":true}]",
                MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfile("the-code", REDIRECT_URI).name()).isEqualTo("octocat");
    }

    @Test
    void code無效時GitHub回200加error_視為驗證失敗且不再往下呼叫() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("""
                {"error":"bad_verification_code","error_description":"The code passed is incorrect or expired."}
                """, MediaType.APPLICATION_JSON));

        assertBadRequest(ErrorMessages.GITHUB_VERIFY_FAILED);
        server.verify();
    }

    @Test
    void 主要email未驗證時不採用其他email() {
        respondToken();
        server.expect(requestTo(USER_URI)).andRespond(withSuccess(
                "{\"id\":12345,\"login\":\"octocat\",\"name\":\"The Octocat\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(EMAILS_URI)).andRespond(withSuccess("""
                [{"email":"primary@example.com","primary":true,"verified":false},
                 {"email":"other@example.com","primary":false,"verified":true}]
                """, MediaType.APPLICATION_JSON));

        assertBadRequest(ErrorMessages.GITHUB_NO_VERIFIED_EMAIL);
    }

    @Test
    void GitHub回錯誤狀態碼時視為驗證失敗() {
        respondToken();
        server.expect(requestTo(USER_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Bad credentials\"}"));

        assertBadRequest(ErrorMessages.GITHUB_VERIFY_FAILED);
    }

    @Test
    void 沒設定clientId或secret時不連線() {
        GithubOAuthClient noSecret = new GithubOAuthClient(properties("client-id", ""), RestClient.create());

        assertThatThrownBy(() -> noSecret.fetchProfile("the-code", REDIRECT_URI))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.GITHUB_NOT_CONFIGURED);
                });
    }

    private void respondToken() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(
                "{\"access_token\":\"gho_token\"}", MediaType.APPLICATION_JSON));
    }

    private void assertBadRequest(String message) {
        assertThatThrownBy(() -> client.fetchProfile("the-code", REDIRECT_URI))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(message);
                });
    }

    private static GithubProperties properties(String clientId, String clientSecret) {
        GithubProperties properties = new GithubProperties();
        properties.setClientId(clientId);
        properties.setClientSecret(clientSecret);
        return properties;
    }
}
