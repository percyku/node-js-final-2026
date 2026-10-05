package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.dto.user.GoogleLoginRequest;
import com.percyku.livefit.dto.user.LoginRequest;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.dto.user.UpdatePasswordRequest;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.entity.UserIdentity;
import com.percyku.livefit.repository.UserIdentityRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.security.GoogleIdTokenVerifier;
import com.percyku.livefit.security.SocialProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import static org.mockito.Mockito.when;

/**
 * 第三方登入的帳號對應。根目錄的黑箱測試拿不到真的 Google ID token，測不到這一段，
 * 所以這裡把 GoogleIdTokenVerifier 換成假的，其餘（含交易與 unique 約束）都走真的資料庫。
 * 需要 livefit 資料庫在線；每個測試用隨機 email，結束後刪掉自己建的資料。
 */
@SpringBootTest
class UserServiceSocialLoginTest {

    private static final String PASSWORD = "Aa123456";
    private static final int CONCURRENCY = 8;

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(user -> {
                userIdentityRepository.deleteAll(identitiesOf(user));
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

        userService.googleLogin(new GoogleLoginRequest(credential));
        assertThat(identitiesOf(created)).hasSize(1);
    }

    @Test
    void 同email的密碼帳號會被綁定_密碼保留() {
        String email = newEmail();
        userService.signup(new SignupRequest("密碼使用者", email, PASSWORD));
        String sub = "sub-" + UUID.randomUUID();

        // Google 給的 email 大小寫不同也要對到同一個帳號
        String credential = googleCredential(sub, email.toUpperCase(), true, "Google 名稱");
        assertThat(userService.googleLogin(new GoogleLoginRequest(credential)).user().name())
                .isEqualTo("密碼使用者");

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identitiesOf(user)).extracting(UserIdentity::getProviderUserId).containsExactly(sub);
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
    void 純第三方登入的帳號不能修改密碼() {
        String email = newEmail();
        userService.googleLogin(new GoogleLoginRequest(
                googleCredential("sub-" + UUID.randomUUID(), email, true, "無密碼")));
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();

        assertThatThrownBy(() -> userService.updatePassword(userId,
                new UpdatePasswordRequest(PASSWORD, "Bb123456", "Bb123456")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(400);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.SOCIAL_ACCOUNT_NO_PASSWORD);
                });
    }

    @Test
    void 同一個Google帳號同時首次登入_全部成功且只建立一個帳號() throws Exception {
        String email = newEmail();
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "併發建立");

        runConcurrently(() -> userService.googleLogin(new GoogleLoginRequest(credential)));

        assertThat(identitiesOf(userRepository.findByEmail(email).orElseThrow())).hasSize(1);
    }

    @Test
    void 同一個Google帳號同時綁定既有帳號_全部成功且只綁定一次() throws Exception {
        String email = newEmail();
        userService.signup(new SignupRequest("併發綁定", email, PASSWORD));
        String credential = googleCredential("sub-" + UUID.randomUUID(), email, true, "併發綁定");

        runConcurrently(() -> userService.googleLogin(new GoogleLoginRequest(credential)));

        assertThat(identitiesOf(userRepository.findByEmail(email).orElseThrow())).hasSize(1);
    }

    private String newEmail() {
        String email = "social-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }

    /** 回傳一個假的 credential，交給 googleLogin 時會被「驗證」成指定的使用者資料 */
    private String googleCredential(String sub, String email, boolean emailVerified, String name) {
        String credential = "credential-" + UUID.randomUUID();
        when(googleIdTokenVerifier.verify(credential)).thenReturn(
                new SocialProfile(UserIdentity.PROVIDER_GOOGLE, sub, email, emailVerified, name));
        return credential;
    }

    private List<UserIdentity> identitiesOf(User user) {
        return userIdentityRepository.findAll().stream()
                .filter(identity -> identity.getUser().getId().equals(user.getId()))
                .toList();
    }

    /** 讓所有執行緒同時起跑；任何一個丟例外，future.get() 就會讓測試失敗 */
    private void runConcurrently(Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENCY; i++) {
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
