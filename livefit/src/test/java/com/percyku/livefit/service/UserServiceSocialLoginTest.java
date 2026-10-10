package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.dto.admin.CoachSignupRequest;
import com.percyku.livefit.dto.user.GoogleLoginRequest;
import com.percyku.livefit.dto.user.LoginRequest;
import com.percyku.livefit.dto.user.OAuthCodeLoginRequest;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.dto.user.UpdateNameRequest;
import com.percyku.livefit.dto.user.UpdatePasswordRequest;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.entity.UserIdentity;
import com.percyku.livefit.repository.CoachRepository;
import com.percyku.livefit.repository.UserIdentityRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.security.FacebookOAuthClient;
import com.percyku.livefit.security.GithubOAuthClient;
import com.percyku.livefit.security.GoogleIdTokenVerifier;
import com.percyku.livefit.security.SocialProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第三方登入的帳號對應。根目錄的黑箱測試拿不到真的 Google ID token，測不到這一段，
 * 所以這裡把 GoogleIdTokenVerifier 與 GithubOAuthClient 換成假的，其餘（含交易與 unique 約束）都走真的資料庫。
 * 需要 livefit 資料庫在線；每個測試用隨機 email，結束後刪掉自己建的資料。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "oauth.redirect-uris=" + UserServiceSocialLoginTest.REDIRECT_URI)
class UserServiceSocialLoginTest {

    static final String REDIRECT_URI = "http://localhost:5173/oauth/callback/github";

    private static final String PASSWORD = "Aa123456";
    private static final String NEW_PASSWORD = "Bb123456";
    private static final int CONCURRENCY = 8;

    @Autowired
    private UserService userService;
    @Autowired
    private AdminCoachService adminCoachService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserIdentityRepository userIdentityRepository;
    @Autowired
    private CoachRepository coachRepository;
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    @MockitoBean
    private GithubOAuthClient githubOAuthClient;
    @MockitoBean
    private FacebookOAuthClient facebookOAuthClient;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(user -> {
                userIdentityRepository.deleteAll(identitiesOf(user));
                coachRepository.findByUserId(user.getId()).ifPresent(coachRepository::delete);
                userRepository.delete(user);
            });
        }
    }

    @Test
    void 沒登入過的Google帳號會建立無密碼帳號_再次登入是同一個帳號() {
        String email = newEmail();
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "小明");

        assertThat(userService.googleLogin(new GoogleLoginRequest(credential)).user().name()).isEqualTo("小明");
        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getPassword()).isNull();
        assertThat(created.getRole()).isEqualTo(User.ROLE_USER);
        // Google 驗證過信箱，之後綁其他平台不會被當成接管
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getTokenVersion()).isZero();

        userService.googleLogin(new GoogleLoginRequest(credential));
        assertThat(identitiesOf(created)).hasSize(1);
    }

    @Test
    void 同email的密碼帳號被Google接管_密碼清除且舊token失效() throws Exception {
        String email = newEmail();
        userService.signup(new SignupRequest("密碼使用者", email, PASSWORD));
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
        String oldToken = userService.login(new LoginRequest(email, PASSWORD)).token();
        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.has_password").value(true));
        String sub = "sub-" + UUID.randomUUID();

        // Google 給的 email 大小寫不同也要對到同一個帳號
        String credential = googleCredential(sub, email.toUpperCase(), true, "Google 名稱");
        var response = userService.googleLogin(new GoogleLoginRequest(credential));
        // 帳號裡的資料不動，名稱仍是原本的
        assertThat(response.user().name()).isEqualTo("密碼使用者");

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).extracting(UserIdentity::getProviderUserId).containsExactly(sub);
        assertThat(user.getPassword()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1);

        // 註冊這個帳號的人不一定是信箱本人，原本的密碼與 token 都不能再用
        assertThatThrownBy(() -> userService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.LOGIN_FAILED));
        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.TOKEN_INVALID));
        mockMvc.perform(get("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.has_password").value(false));
    }

    @Test
    void Facebook建立的帳號被Google接管_Facebook綁定被刪除且無法再登入() {
        String email = newEmail();
        String facebookId = "fb-" + UUID.randomUUID();
        userService.facebookLogin(
                new OAuthCodeLoginRequest(facebookCode(facebookId, email, "冒用者"), REDIRECT_URI));
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();

        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "信箱本人")));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).extracting(UserIdentity::getProvider)
                .containsExactly(UserIdentity.PROVIDER_GOOGLE);
        assertThat(user.getTokenVersion()).isEqualTo(1);

        String code = facebookCode(facebookId, email, "冒用者");
        assertThatThrownBy(() -> userService.facebookLogin(new OAuthCodeLoginRequest(code, REDIRECT_URI)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.SOCIAL_EMAIL_REGISTERED);
                });
    }

    @Test
    void 信箱驗證過的帳號再綁其他平台_密碼與token都保留() {
        String email = newEmail();
        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "Google 名稱")));
        userService.updatePassword(freshLogin(email), new UpdatePasswordRequest(null, PASSWORD, PASSWORD));

        String code = githubCode("gh-" + UUID.randomUUID(), email, "octocat");
        userService.githubLogin(new OAuthCodeLoginRequest(code, REDIRECT_URI));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).extracting(UserIdentity::getProvider)
                .containsExactlyInAnyOrder(UserIdentity.PROVIDER_GOOGLE, UserIdentity.PROVIDER_GITHUB);
        assertThat(user.getTokenVersion()).isZero();
        assertThat(userService.login(new LoginRequest(email, PASSWORD)).token()).isNotBlank();
    }

    @Test
    void 帳號已綁定另一個Google帳號時回409() {
        String email = newEmail();
        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "先來的")));

        String other = googleCredential("sub-" + UUID.randomUUID(), email, true, "後到的");
        assertThatThrownBy(() -> userService.googleLogin(new GoogleLoginRequest(other)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.EMAIL_TAKEN);
                });
    }

    @Test
    void email未經Google驗證時回400_不建立帳號() {
        String email = newEmail();
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, false, "未驗證");

        assertThatThrownBy(() -> userService.googleLogin(new GoogleLoginRequest(credential)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.GOOGLE_VERIFY_FAILED);
                });
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void 無密碼帳號可以直接設定密碼_之後修改就要驗舊密碼() {
        String email = newEmail();
        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "無密碼")));

        // 兩次輸入不一致、不合規則照樣要擋
        assertThatThrownBy(() -> userService.updatePassword(freshLogin(email),
                new UpdatePasswordRequest(null, PASSWORD, NEW_PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.PASSWORD_CONFIRM_MISMATCH));
        assertThatThrownBy(() -> userService.updatePassword(freshLogin(email),
                new UpdatePasswordRequest(null, "short", "short")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.PASSWORD_RULE));

        // 前端可能把空的舊密碼欄位一起送來，要忽略
        userService.updatePassword(freshLogin(email), new UpdatePasswordRequest("", PASSWORD, PASSWORD));
        assertThat(userService.login(new LoginRequest(email, PASSWORD)).token()).isNotBlank();

        assertThatThrownBy(() -> userService.updatePassword(freshLogin(email),
                new UpdatePasswordRequest("Zz987654", NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.PASSWORD_WRONG);
                });
    }

    @Test
    void 無密碼帳號設定密碼要剛登入過_太久以前的token回400() {
        String email = newEmail();
        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "無密碼")));
        User user = userRepository.findByEmail(email).orElseThrow();
        Instant tooOld = Instant.now().minus(UserService.SET_PASSWORD_LOGIN_WINDOW).minusSeconds(1);

        for (Instant issuedAt : new Instant[]{tooOld, null}) {
            assertThatThrownBy(() -> userService.updatePassword(new AuthUser(user, issuedAt),
                    new UpdatePasswordRequest(null, PASSWORD, PASSWORD)))
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(400);
                        assertThat(ex.getMessage()).isEqualTo(ErrorMessages.SET_PASSWORD_RELOGIN);
                    });
        }
        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isNull();
    }

    @Test
    void 接管前通過驗證的請求不能在接管後設定密碼或改名() {
        String email = newEmail();
        userService.signup(new SignupRequest("冒用者", email, PASSWORD));
        // 冒用者的請求已通過 JwtAuthenticationFilter，手上是接管前的 token_version
        AuthUser beforeTakeOver = freshLogin(email);

        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "信箱本人")));

        assertThatThrownBy(() -> userService.updatePassword(beforeTakeOver,
                new UpdatePasswordRequest(null, NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(401);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.TOKEN_INVALID);
                });
        assertThatThrownBy(() -> userService.updateName(beforeTakeOver, new UpdateNameRequest("改名")))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(401));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getName()).isEqualTo("冒用者");
    }

    @Test
    void 同一個Google帳號同時首次登入_全部成功且只建立一個帳號() throws Exception {
        String email = newEmail();
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "併發建立");

        runConcurrently(() -> userService.googleLogin(new GoogleLoginRequest(credential)));

        assertThat(identitiesOf(userRepository.findByEmail(email).orElseThrow())).hasSize(1);
    }

    @Test
    void 同一個Google帳號同時接管既有帳號_全部成功且只接管一次() throws Exception {
        String email = newEmail();
        userService.signup(new SignupRequest("併發綁定", email, PASSWORD));
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "併發綁定");

        runConcurrently(() -> userService.googleLogin(new GoogleLoginRequest(credential)));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).hasSize(1);
        assertThat(user.getPassword()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void Google與GitHub同時接管Facebook建立的帳號_都成功且只接管一次() throws Exception {
        String email = newEmail();
        userService.facebookLogin(new OAuthCodeLoginRequest(
                facebookCode("fb-" + UUID.randomUUID(), email, "冒用者"), REDIRECT_URI));
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "信箱本人");
        String code = githubCode("gh-" + UUID.randomUUID(), email, "octocat");

        runTogether(
                () -> userService.googleLogin(new GoogleLoginRequest(credential)),
                () -> userService.githubLogin(new OAuthCodeLoginRequest(code, REDIRECT_URI)));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).extracting(UserIdentity::getProvider)
                .containsExactlyInAnyOrder(UserIdentity.PROVIDER_GOOGLE, UserIdentity.PROVIDER_GITHUB);
        // 先到的接管，後到的看到的已經是驗證過的帳號，只新增綁定
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void 接管同時有人改名改密碼升級教練_接管的結果不會被蓋回去() throws Exception {
        String email = newEmail();
        userService.signup(new SignupRequest("冒用者", email, PASSWORD));
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "信箱本人");

        // 這三個都會把整列 users 寫回。它們各自成功或被拒絕都可以，但不能把舊的密碼與 token_version 寫回去
        runTogether(
                () -> userService.googleLogin(new GoogleLoginRequest(credential)),
                ignoringApiException(() -> userService.updateName(freshLogin(email), new UpdateNameRequest("改名"))),
                ignoringApiException(() -> {
                    userService.updatePassword(freshLogin(email),
                            new UpdatePasswordRequest(PASSWORD, NEW_PASSWORD, NEW_PASSWORD));
                    return null;
                }),
                ignoringApiException(() -> adminCoachService.signupCoach(userId,
                        new CoachSignupRequest(3, "教練簡介", null))));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(identitiesOf(user)).hasSize(1);
    }

    @Test
    void GitHub登入會綁定同email的帳號_且可與Google並存() {
        String email = newEmail();
        userService.signup(new SignupRequest("密碼使用者", email, PASSWORD));
        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "Google 名稱")));

        String code = githubCode("gh-" + UUID.randomUUID(), email, "octocat");
        assertThat(userService.githubLogin(new OAuthCodeLoginRequest(code, REDIRECT_URI)).user().name())
                .isEqualTo("密碼使用者");

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).extracting(UserIdentity::getProvider)
                .containsExactlyInAnyOrder(UserIdentity.PROVIDER_GOOGLE, UserIdentity.PROVIDER_GITHUB);
        // 只有第一次（Google）是接管，GitHub 進來時信箱已驗證過，不會再讓 token 失效一次
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void 沒登入過的GitHub帳號會建立無密碼帳號() {
        String email = newEmail();
        String code = githubCode("gh-" + UUID.randomUUID(), email, "octocat");

        assertThat(userService.githubLogin(new OAuthCodeLoginRequest(code, REDIRECT_URI)).user().name())
                .isEqualTo("octocat");
        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getPassword()).isNull();
        assertThat(created.isEmailVerified()).isTrue();
    }

    @Test
    void 沒登入過的Facebook帳號會建立無密碼帳號_再次登入是同一個帳號() {
        String email = newEmail();
        String facebookId = "fb-" + UUID.randomUUID();

        assertThat(userService.facebookLogin(
                new OAuthCodeLoginRequest(facebookCode(facebookId, email, "王小明"), REDIRECT_URI)).user().name())
                .isEqualTo("王小明");
        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getPassword()).isNull();
        // Facebook 不保證信箱驗證過
        assertThat(created.isEmailVerified()).isFalse();

        // 授權碼每次都不同，但同一個 Facebook 帳號要對到同一個使用者
        userService.facebookLogin(
                new OAuthCodeLoginRequest(facebookCode(facebookId, email, "王小明"), REDIRECT_URI));
        assertThat(identitiesOf(created)).extracting(UserIdentity::getProvider)
                .containsExactly(UserIdentity.PROVIDER_FACEBOOK);
    }

    @Test
    void Facebook的email已有帳號時回409_不會自動綁定() {
        String email = newEmail();
        userService.signup(new SignupRequest("密碼使用者", email, PASSWORD));

        String code = facebookCode("fb-" + UUID.randomUUID(), email, "冒用者");
        assertThatThrownBy(() -> userService.facebookLogin(new OAuthCodeLoginRequest(code, REDIRECT_URI)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.SOCIAL_EMAIL_REGISTERED);
                });

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).isEmpty();
        // 未驗證的登入不能觸發接管，密碼要還在
        assertThat(userService.login(new LoginRequest(email, PASSWORD)).token()).isNotBlank();
    }

    @Test
    void redirect_uri不在白名單時回400_不會拿code去換token() {
        assertThatThrownBy(() -> userService.githubLogin(
                new OAuthCodeLoginRequest("any-code", "https://evil.example.com/oauth/callback/github")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.INVALID_FIELDS);
                });
        verify(githubOAuthClient, never()).fetchProfile(anyString(), anyString());
    }

    private String newEmail() {
        String email = "social-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }

    /** 模擬剛登入、已通過 JwtAuthenticationFilter 的請求：帶著當下的 token_version 與簽發時間 */
    private AuthUser freshLogin(String email) {
        return new AuthUser(userRepository.findByEmail(email).orElseThrow(), Instant.now());
    }

    /** 回傳一個假的 credential，交給 googleLogin 時會被「驗證」成指定的使用者資料 */
    private String googleCredential(String sub, String email, boolean emailVerified, String name) {
        String credential = "credential-" + UUID.randomUUID();
        when(googleIdTokenVerifier.verify(credential)).thenReturn(
                new SocialProfile(UserIdentity.PROVIDER_GOOGLE, sub, email, emailVerified, name));
        return credential;
    }

    /** 回傳一個假的授權碼，交給 githubLogin 時會被「換」成指定的使用者資料 */
    private String facebookCode(String facebookId, String email, String name) {
        String code = "code-" + UUID.randomUUID();
        // Facebook 不提供 email 是否驗證過的旗標，FacebookOAuthClient 一律回 false
        when(facebookOAuthClient.fetchProfile(code, REDIRECT_URI)).thenReturn(
                new SocialProfile(UserIdentity.PROVIDER_FACEBOOK, facebookId, email, false, name));
        return code;
    }

    private String githubCode(String githubId, String email, String name) {
        String code = "code-" + UUID.randomUUID();
        when(githubOAuthClient.fetchProfile(code, REDIRECT_URI)).thenReturn(
                new SocialProfile(UserIdentity.PROVIDER_GITHUB, githubId, email, true, name));
        return code;
    }

    private List<UserIdentity> identitiesOf(User user) {
        return userIdentityRepository.findAll().stream()
                .filter(identity -> identity.getUser().getId().equals(user.getId()))
                .toList();
    }

    /** 與接管搶著執行的操作可能成功、也可能因為 token 已作廢等原因被拒絕，兩者都算正常 */
    private Callable<Object> ignoringApiException(Callable<?> task) {
        return () -> {
            try {
                return task.call();
            } catch (ApiException expected) {
                return null;
            }
        };
    }

    /** 同一件事開 CONCURRENCY 個執行緒同時做 */
    private void runConcurrently(Callable<?> task) throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            tasks.add(task);
        }
        runTogether(tasks.toArray(Callable<?>[]::new));
    }

    /** 讓所有執行緒同時起跑；任何一個丟例外，future.get() 就會讓測試失敗 */
    private void runTogether(Callable<?>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
