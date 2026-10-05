package com.percyku.livefit.security;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.config.FacebookProperties;
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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Facebook 的回應用 MockRestServiceServer 假造，不會真的連線 */
class FacebookOAuthClientTest {

    private static final String TOKEN_URI = "https://graph.facebook.com/v26.0/oauth/access_token";
    private static final String ME_URI = "https://graph.facebook.com/v26.0/me?fields=id,name,email";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback/facebook";

    private MockRestServiceServer server;
    private FacebookOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FacebookOAuthClient(properties("app-id", "app-secret"), builder.build());
    }

    @Test
    void 成功時回傳使用者資料_email一律視為未驗證() {
        server.expect(requestTo(startsWith(TOKEN_URI + "?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("client_id", "app-id"))
                .andExpect(queryParam("client_secret", "app-secret"))
                .andExpect(queryParam("code", "the-code"))
                .andExpect(queryParam("redirect_uri", REDIRECT_URI))
                .andRespond(withSuccess("""
                        {"access_token":"EAAB-token","token_type":"bearer","expires_in":5183944}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ME_URI))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer EAAB-token"))
                .andRespond(withSuccess("""
                        {"id":"10001234567890","name":"王小明","email":"ming@example.com"}
                        """, MediaType.APPLICATION_JSON));

        SocialProfile profile = client.fetchProfile("the-code", REDIRECT_URI);

        assertThat(profile).isEqualTo(new SocialProfile(
                UserIdentity.PROVIDER_FACEBOOK, "10001234567890", "ming@example.com", false, "王小明"));
        server.verify();
    }

    @Test
    void 帳號沒有email時無法登入() {
        respondToken();
        server.expect(requestTo(ME_URI)).andRespond(withSuccess(
                "{\"id\":\"10001234567890\",\"name\":\"王小明\"}", MediaType.APPLICATION_JSON));

        assertBadRequest(ErrorMessages.FACEBOOK_NO_EMAIL);
    }

    @Test
    void code無效時Facebook回400_視為驗證失敗且不再往下呼叫() {
        server.expect(requestTo(startsWith(TOKEN_URI + "?")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"This authorization code has been used.",
                                 "type":"OAuthException","code":100,"error_subcode":36009}}
                                """));

        assertBadRequest(ErrorMessages.FACEBOOK_VERIFY_FAILED);
        server.verify();
    }

    @Test
    void 取個人資料失敗時視為驗證失敗() {
        respondToken();
        server.expect(requestTo(ME_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"Invalid OAuth access token.\",\"type\":\"OAuthException\"}}"));

        assertBadRequest(ErrorMessages.FACEBOOK_VERIFY_FAILED);
    }

    @Test
    void 沒設定appId或密鑰時不連線() {
        FacebookOAuthClient noSecret = new FacebookOAuthClient(properties("app-id", ""), RestClient.create());

        assertThatThrownBy(() -> noSecret.fetchProfile("the-code", REDIRECT_URI))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.FACEBOOK_NOT_CONFIGURED);
                });
    }

    private void respondToken() {
        server.expect(requestTo(startsWith(TOKEN_URI + "?"))).andRespond(withSuccess(
                "{\"access_token\":\"EAAB-token\"}", MediaType.APPLICATION_JSON));
    }

    private void assertBadRequest(String message) {
        assertThatThrownBy(() -> client.fetchProfile("the-code", REDIRECT_URI))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(message);
                });
    }

    private static FacebookProperties properties(String appId, String appSecret) {
        FacebookProperties properties = new FacebookProperties();
        properties.setAppId(appId);
        properties.setAppSecret(appSecret);
        return properties;
    }
}
