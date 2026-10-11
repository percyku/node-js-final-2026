package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.config.AdminSeeder;
import com.percyku.livefit.dto.user.GoogleLoginRequest;
import com.percyku.livefit.dto.user.LoginRequest;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.entity.Coach;
import com.percyku.livefit.entity.Skill;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.entity.UserIdentity;
import com.percyku.livefit.repository.CoachRepository;
import com.percyku.livefit.repository.CoachWithSkillRepository;
import com.percyku.livefit.repository.SkillRepository;
import com.percyku.livefit.repository.UserIdentityRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.security.FacebookOAuthClient;
import com.percyku.livefit.security.GithubOAuthClient;
import com.percyku.livefit.security.GoogleIdTokenVerifier;
import com.percyku.livefit.security.JwtTokenProvider;
import com.percyku.livefit.security.SocialProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M7 管理員後台：預設管理員的建立、兩支需要 ADMIN 的 API、以及 ADMIN 帳號的保護。
 * 根目錄的黑箱測試是給 Node 版驗收用的，沒有 ADMIN 的案例，所以寫在這裡。
 * 需要 livefit 資料庫在線；管理員用這個測試專屬的 email，全部跑完後刪掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        // 刻意帶大寫：AdminSeeder 要存成小寫，登入才查得到
        "admin.email=" + AdminUserApiTest.ADMIN_EMAIL_CONFIGURED,
        "admin.password=" + AdminUserApiTest.ADMIN_PASSWORD})
class AdminUserApiTest {

    static final String ADMIN_EMAIL_CONFIGURED = "M7-Admin-Test@example.com";
    static final String ADMIN_PASSWORD = "Admin123";

    private static final String ADMIN_EMAIL = ADMIN_EMAIL_CONFIGURED.toLowerCase();
    private static final String PASSWORD = "Aa123456";
    private static final String DEFAULT_FORM = """
            {"experience_years": 0, "description": "", "profile_image_url": ""}""";

    @Autowired
    private UserService userService;
    @Autowired
    private AdminCoachService adminCoachService;
    @Autowired
    private AdminSeeder adminSeeder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserIdentityRepository userIdentityRepository;
    @Autowired
    private CoachRepository coachRepository;
    @Autowired
    private CoachWithSkillRepository coachWithSkillRepository;
    @Autowired
    private SkillRepository skillRepository;
    @Autowired
    private JwtTokenProvider tokenProvider;
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
        createdEmails.forEach(this::deleteUser);
        createdEmails.clear();
    }

    @AfterAll
    void removeAdmin() {
        deleteUser(ADMIN_EMAIL);
    }

    // ===== 預設管理員 =====

    @Test
    void 啟動後預設管理員已建立_可用密碼登入且token的role是ADMIN() {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(User.ROLE_ADMIN);
        assertThat(admin.getPassword()).isNotEqualTo(ADMIN_PASSWORD);

        // 登入時輸入的大小寫不影響
        String token = userService.login(new LoginRequest(ADMIN_EMAIL_CONFIGURED, ADMIN_PASSWORD)).token();
        assertThat(tokenProvider.parse(token).get("role", String.class)).isEqualTo(User.ROLE_ADMIN);
    }

    @Test
    void 重跑seeder不會重複建立_也不會覆寫已存在的帳號() {
        User before = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        adminSeeder.run(new DefaultApplicationArguments());

        User after = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPassword()).isEqualTo(before.getPassword());
        assertThat(userRepository.findAll().stream().filter(u -> ADMIN_EMAIL.equals(u.getEmail()))).hasSize(1);
    }

    // ===== 權限 =====

    @Test
    void 沒登入打M7的API回401請先登入() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.FIXED_NOT_LOGIN));
        mockMvc.perform(promote(UUID.randomUUID(), DEFAULT_FORM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.FIXED_NOT_LOGIN));
    }

    @Test
    void 一般使用者與教練打M7的API回403() throws Exception {
        String userToken = tokenOf(newUser("一般使用者"));
        String coachEmail = newUser("教練");
        mockMvc.perform(promote(idOf(coachEmail), DEFAULT_FORM).with(bearer(adminToken())))
                .andExpect(status().isCreated());
        String coachToken = tokenOf(coachEmail);

        for (String token : List.of(userToken, coachToken)) {
            mockMvc.perform(get("/api/admin/users").with(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("failed"))
                    .andExpect(jsonPath("$.message").value(ErrorMessages.NOT_ADMIN));
            mockMvc.perform(promote(idOf(coachEmail), DEFAULT_FORM).with(bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(ErrorMessages.NOT_ADMIN));
        }
    }

    @Test
    void 教練端點的權限不足仍是401使用者尚未成為教練() throws Exception {
        mockMvc.perform(get("/api/admin/coaches").with(bearer(adminToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.NOT_COACH));
    }

    // ===== 使用者列表 =====

    @Test
    void 管理員可列出使用者_不含密碼_可分頁與依角色篩選() throws Exception {
        String email = newUser("列表使用者");
        newUser("列表使用者二");
        String token = adminToken();

        // 新到舊排序，剛建立的兩筆會在第一頁
        mockMvc.perform(get("/api/admin/users").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.users[*].email", hasItem(email)))
                .andExpect(jsonPath("$.data.users[0].id").exists())
                .andExpect(jsonPath("$.data.users[0].created_at").exists())
                .andExpect(jsonPath("$.data.users[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.users[0].token_version").doesNotExist())
                .andExpect(jsonPath("$.data.pagination.page").value(1))
                .andExpect(jsonPath("$.data.pagination.per").value(10));

        mockMvc.perform(get("/api/admin/users?per=1&page=2").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users", hasSize(1)))
                .andExpect(jsonPath("$.data.pagination.page").value(2))
                .andExpect(jsonPath("$.data.pagination.total").value((int) userRepository.count()));

        mockMvc.perform(get("/api/admin/users?role=admin&per=100").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users[*].role", everyItem(is(User.ROLE_ADMIN))))
                .andExpect(jsonPath("$.data.users[*].email", hasItem(ADMIN_EMAIL)));
    }

    @Test
    void 列表的查詢參數不合法回400() throws Exception {
        String token = adminToken();
        for (String query : List.of("page=0", "page=abc", "per=-1", "per=101", "role=OWNER")) {
            mockMvc.perform(get("/api/admin/users?" + query).with(bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));
        }
    }

    // ===== 搜尋 =====

    @Test
    void 可用名稱或email搜尋_不分大小寫_可與角色併用() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String byName = newUser("搜尋Name" + tag);
        String other = newUser("不相干的人");
        String token = adminToken();

        // 名稱：部分符合、大小寫不同
        mockMvc.perform(get("/api/admin/users").param("keyword", " name" + tag.toUpperCase() + " ")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users[*].email", contains(byName)))
                .andExpect(jsonPath("$.data.pagination.total").value(1));

        // email：newUser 產生的 email 含隨機 uuid，取 @ 前面那段
        mockMvc.perform(get("/api/admin/users").param("keyword", other.split("@")[0].toUpperCase())
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users[*].email", contains(other)));

        mockMvc.perform(get("/api/admin/users").param("keyword", "name" + tag).param("role", "COACH")
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users", hasSize(0)))
                .andExpect(jsonPath("$.data.pagination.total").value(0));

        // 空白等同沒給
        mockMvc.perform(get("/api/admin/users").param("keyword", "  ").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.total").value((int) userRepository.count()));
    }

    @Test
    void 搜尋的百分比與底線是一般字元_過長回400() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String literal = newUser("a_b%c" + tag);
        newUser("aXbYc" + tag);
        String token = adminToken();

        mockMvc.perform(get("/api/admin/users").param("keyword", "a_b%c" + tag).with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users[*].email", contains(literal)));
        // 當成萬用字元的話會把所有人都撈出來
        mockMvc.perform(get("/api/admin/users").param("keyword", "%" + tag + "_").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users", hasSize(0)));

        mockMvc.perform(get("/api/admin/users").param("keyword", "x".repeat(101)).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));
    }

    // ===== 建立管理員 =====

    @Test
    void 管理員可建立另一位管理員_email存小寫_可登入且不能用第三方登入() throws Exception {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);

        mockMvc.perform(createAdmin("新管理員", email.toUpperCase(), PASSWORD).with(bearer(adminToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").exists())
                .andExpect(jsonPath("$.data.user.name").value("新管理員"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_ADMIN))
                .andExpect(jsonPath("$.data.user.created_at").exists())
                .andExpect(jsonPath("$.data.user.password").doesNotExist());

        String token = tokenOf(email);
        assertThat(tokenProvider.parse(token).get("role", String.class)).isEqualTo(User.ROLE_ADMIN);
        mockMvc.perform(get("/api/admin/users").with(bearer(token)))
                .andExpect(status().isOk());

        String credential = "credential-" + UUID.randomUUID();
        when(googleIdTokenVerifier.verify(credential)).thenReturn(new SocialProfile(
                UserIdentity.PROVIDER_GOOGLE, "sub-" + UUID.randomUUID(), email, true, "信箱主人"));
        assertThatThrownBy(() -> userService.googleLogin(new GoogleLoginRequest(credential)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(409));
        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isNotNull();
    }

    @Test
    void 建立管理員的欄位規則與註冊相同_email重複回409() throws Exception {
        String token = adminToken();
        String taken = newUser("已註冊的會員");
        String fresh = "admin-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(fresh);

        mockMvc.perform(createAdmin("", fresh, PASSWORD).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));
        mockMvc.perform(createAdmin("名".repeat(51), fresh, PASSWORD).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));
        mockMvc.perform(post("/api/admin/users/admins").with(bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(createAdmin("弱密碼", fresh, "12345678").with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.PASSWORD_RULE));
        assertThat(userRepository.findByEmail(fresh)).isEmpty();

        mockMvc.perform(createAdmin("搶別人的信箱", taken.toUpperCase(), PASSWORD).with(bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.EMAIL_TAKEN));
        assertThat(userRepository.findByEmail(taken).orElseThrow().getRole()).isEqualTo(User.ROLE_USER);
    }

    // ===== 重設管理員密碼 =====

    @Test
    void 重設管理員密碼_舊密碼與舊token失效_之後可以自己改掉() throws Exception {
        String email = newAdmin("忘記密碼的管理員");
        String oldToken = tokenOf(email);

        mockMvc.perform(resetPassword(idOf(email)).with(bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        assertThatThrownBy(() -> userService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ErrorMessages.LOGIN_FAILED));
        mockMvc.perform(get("/api/admin/users").with(bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.TOKEN_INVALID));

        String newToken = userService.login(
                new LoginRequest(email, AdminUserService.RESET_PASSWORD)).token();
        mockMvc.perform(put("/api/users/password").with(bearer(newToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "%s", "new_password": "Cc123456", "confirm_new_password": "Cc123456"}"""
                                .formatted(AdminUserService.RESET_PASSWORD)))
                .andExpect(status().isOk());
        assertThat(userService.login(new LoginRequest(email, "Cc123456")).token()).isNotBlank();
        assertThat(userRepository.findByEmail(email).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    void 重設密碼只能對其他管理員() throws Exception {
        String token = adminToken();
        String userEmail = newUser("一般會員");
        String coachEmail = newUser("教練");
        mockMvc.perform(promote(idOf(coachEmail), DEFAULT_FORM).with(bearer(token)))
                .andExpect(status().isCreated());

        for (String email : List.of(userEmail, coachEmail)) {
            mockMvc.perform(resetPassword(idOf(email)).with(bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ErrorMessages.ONLY_ADMIN_CAN_BE_RESET));
            // 密碼沒被動到
            assertThat(tokenOf(email)).isNotBlank();
        }

        mockMvc.perform(resetPassword(idOf(ADMIN_EMAIL)).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.CANNOT_RESET_SELF));
        mockMvc.perform(get("/api/admin/users").with(bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(resetPassword(UUID.randomUUID()).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.USER_NOT_FOUND));
    }

    @Test
    void 建立管理員與重設密碼_沒登入回401_一般使用者回403() throws Exception {
        String target = newAdmin("不該被動到的管理員");
        String userToken = tokenOf(newUser("一般使用者"));
        String fresh = "admin-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(fresh);

        mockMvc.perform(createAdmin("匿名", fresh, PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.FIXED_NOT_LOGIN));
        mockMvc.perform(resetPassword(idOf(target)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(createAdmin("想當管理員", fresh, PASSWORD).with(bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.NOT_ADMIN));
        mockMvc.perform(resetPassword(idOf(target)).with(bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.NOT_ADMIN));

        assertThat(userRepository.findByEmail(fresh)).isEmpty();
        assertThat(tokenOf(target)).isNotBlank();
    }

    // ===== 升級教練 =====

    @Test
    void 管理員用表單預設值升級使用者_角色變教練且舊token失效() throws Exception {
        String email = newUser("準教練");
        String oldToken = tokenOf(email);

        mockMvc.perform(promote(idOf(email), DEFAULT_FORM).with(bearer(adminToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.name").value("準教練"))
                .andExpect(jsonPath("$.data.user.role").value(User.ROLE_COACH))
                .andExpect(jsonPath("$.data.coach.experience_years").value(0))
                .andExpect(jsonPath("$.data.coach.user_id").value(idOf(email).toString()));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(User.ROLE_COACH);
        assertThat(user.getTokenVersion()).isEqualTo(1);
        Coach coach = coachRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(coach.getExperienceYears()).isZero();

        // 前端靠 token 的 role 決定介面，舊 token 作廢才會被導去重新登入
        mockMvc.perform(get("/api/users/profile").with(bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorMessages.TOKEN_INVALID));
        // 密碼沒動，重新登入後能用教練後台
        mockMvc.perform(get("/api/admin/coaches").with(bearer(tokenOf(email))))
                .andExpect(status().isOk());
    }

    @Test
    void 用預設值升級的教練補齊資料後可以儲存_年資維持0也可以() throws Exception {
        String email = newUser("待補資料的教練");
        mockMvc.perform(promote(idOf(email), DEFAULT_FORM).with(bearer(adminToken())))
                .andExpect(status().isCreated());
        String token = tokenOf(email);
        Skill skill = new Skill();
        skill.setName("admin-test-" + UUID.randomUUID().toString().substring(0, 8));
        UUID skillId = skillRepository.save(skill).getId();

        try {
            // 簡介、照片、技能在更新時是必填，只補年資以外的三項
            mockMvc.perform(put("/api/admin/coaches").with(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"experience_years": 0, "description": "", "profile_image_url": "", "skill_ids": []}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));

            mockMvc.perform(put("/api/admin/coaches").with(bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"experience_years": 0, "description": "新手教練",
                                     "profile_image_url": "https://example.com/a.png", "skill_ids": ["%s"]}"""
                                    .formatted(skillId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.experience_years").value(0))
                    .andExpect(jsonPath("$.data.description").value("新手教練"));
        } finally {
            coachRepository.findByUserId(idOf(email))
                    .ifPresent(coach -> coachWithSkillRepository.deleteAll(
                            coachWithSkillRepository.findByCoachId(coach.getId())));
            skillRepository.deleteById(skillId);
        }
    }

    @Test
    void 升級時有填的欄位會寫入() throws Exception {
        String email = newUser("有資料的教練");

        mockMvc.perform(promote(idOf(email), """
                        {"experience_years": 5, "description": "重訓十年", "profile_image_url": "https://example.com/a.png"}""")
                        .with(bearer(adminToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.coach.experience_years").value(5))
                .andExpect(jsonPath("$.data.coach.description").value("重訓十年"))
                .andExpect(jsonPath("$.data.coach.profile_image_url").value("https://example.com/a.png"));
    }

    @Test
    void 升級的欄位不合法回400_使用者維持原角色() throws Exception {
        String email = newUser("欄位錯誤");
        String token = adminToken();

        for (String body : List.of(
                "{}",
                """
                        {"experience_years": -1, "description": "x"}""",
                """
                        {"experience_years": 1, "description": "x", "profile_image_url": "http://example.com/a.png"}""")) {
            mockMvc.perform(promote(idOf(email), body).with(bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));
        }
        // 沒帶 body
        mockMvc.perform(post("/api/admin/users/{id}/coach", idOf(email)).with(bearer(token)))
                .andExpect(status().isBadRequest());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(User.ROLE_USER);
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void 升級不存在的使用者回400_重複升級回409_升級管理員回400() throws Exception {
        String token = adminToken();
        String email = newUser("重複升級");

        mockMvc.perform(promote(UUID.randomUUID(), DEFAULT_FORM).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.USER_NOT_FOUND));

        mockMvc.perform(promote(idOf(email), DEFAULT_FORM).with(bearer(token)))
                .andExpect(status().isCreated());
        mockMvc.perform(promote(idOf(email), DEFAULT_FORM).with(bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorMessages.ALREADY_COACH));

        mockMvc.perform(promote(idOf(ADMIN_EMAIL), DEFAULT_FORM).with(bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.ONLY_USER_CAN_BE_PROMOTED));
        assertThat(userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().isAdmin()).isTrue();
    }

    // ===== 管理員帳號的保護 =====

    @Test
    void M3的公開端點不能把管理員改成教練() throws Exception {
        UUID adminId = idOf(ADMIN_EMAIL);

        mockMvc.perform(post("/api/admin/coaches/{id}", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"experience_years": 3, "description": "想當教練的陌生人"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.ONLY_USER_CAN_BE_PROMOTED));

        assertThat(userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().isAdmin()).isTrue();
        assertThat(coachRepository.findByUserId(adminId)).isEmpty();
    }

    @Test
    void M3的公開端點行為不變_年資0被拒絕且不作廢token() throws Exception {
        String email = newUser("自己升級");
        String token = tokenOf(email);

        mockMvc.perform(post("/api/admin/coaches/{id}", idOf(email))
                        .contentType(MediaType.APPLICATION_JSON).content(DEFAULT_FORM))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_FIELDS));

        mockMvc.perform(post("/api/admin/coaches/{id}", idOf(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"experience_years": 3, "description": "自己升級"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/coaches").with(bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void 用管理員的email走Google登入回409_密碼與角色不變() {
        User before = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        String credential = "credential-" + UUID.randomUUID();
        // Google 驗證過的信箱原本會接管沒驗證過的帳號
        when(googleIdTokenVerifier.verify(credential)).thenReturn(new SocialProfile(
                UserIdentity.PROVIDER_GOOGLE, "sub-" + UUID.randomUUID(), ADMIN_EMAIL_CONFIGURED, true, "信箱主人"));

        assertThatThrownBy(() -> userService.googleLogin(new GoogleLoginRequest(credential)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo(ErrorMessages.SOCIAL_EMAIL_REGISTERED);
                });

        User after = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(after.getPassword()).isEqualTo(before.getPassword());
        assertThat(after.getRole()).isEqualTo(User.ROLE_ADMIN);
        assertThat(after.getTokenVersion()).isEqualTo(before.getTokenVersion());
        assertThat(after.isEmailVerified()).isFalse();
        assertThat(identitiesOf(after)).isEmpty();
    }

    // ===== 輔助 =====

    private String newUser(String name) {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        userService.signup(new SignupRequest(name, email, PASSWORD));
        return email;
    }

    /** 建立一位密碼是 PASSWORD 的管理員 */
    private String newAdmin(String name) throws Exception {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        mockMvc.perform(createAdmin(name, email, PASSWORD).with(bearer(adminToken())))
                .andExpect(status().isCreated());
        return email;
    }

    private MockHttpServletRequestBuilder createAdmin(String name, String email, String password) {
        return post("/api/admin/users/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "email": "%s", "password": "%s"}""".formatted(name, email, password));
    }

    private MockHttpServletRequestBuilder resetPassword(UUID userId) {
        return post("/api/admin/users/{id}/reset-password", userId);
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private String tokenOf(String email) {
        return userService.login(new LoginRequest(email, PASSWORD)).token();
    }

    private String adminToken() {
        return userService.login(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD)).token();
    }

    private MockHttpServletRequestBuilder promote(UUID userId, String body) {
        return post("/api/admin/users/{id}/coach", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private List<UserIdentity> identitiesOf(User user) {
        return userIdentityRepository.findAll().stream()
                .filter(identity -> identity.getUser().getId().equals(user.getId()))
                .toList();
    }

    private void deleteUser(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            userIdentityRepository.deleteAll(identitiesOf(user));
            coachRepository.findByUserId(user.getId()).ifPresent(coachRepository::delete);
            userRepository.delete(user);
        });
    }
}
