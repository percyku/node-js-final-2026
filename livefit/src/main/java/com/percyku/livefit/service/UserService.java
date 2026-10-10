package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.config.OAuthProperties;
import com.percyku.livefit.dto.user.CreditPurchaseResponse;
import com.percyku.livefit.dto.user.GoogleLoginRequest;
import com.percyku.livefit.dto.user.LoginRequest;
import com.percyku.livefit.dto.user.LoginResponse;
import com.percyku.livefit.dto.user.OAuthCodeLoginRequest;
import com.percyku.livefit.dto.user.ProfileResponse;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.dto.user.SignupResponse;
import com.percyku.livefit.dto.user.UpdateNameRequest;
import com.percyku.livefit.dto.user.UpdateNameResponse;
import com.percyku.livefit.dto.user.UpdatePasswordRequest;
import com.percyku.livefit.dto.user.UserCoursesResponse;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.entity.UserIdentity;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import com.percyku.livefit.repository.UserIdentityRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.security.AuthUser;
import com.percyku.livefit.security.FacebookOAuthClient;
import com.percyku.livefit.security.GithubOAuthClient;
import com.percyku.livefit.security.GoogleIdTokenVerifier;
import com.percyku.livefit.security.JwtTokenProvider;
import com.percyku.livefit.security.SocialProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/** 對應 backend/controllers/users.js 的業務邏輯，另加 Node 版沒有的第三方登入 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** 無密碼帳號設定密碼時，要求這次登入（token 的簽發時間）在這段時間內 */
    static final Duration SET_PASSWORD_LOGIN_WINDOW = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CourseBookingRepository courseBookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GithubOAuthClient githubOAuthClient;
    private final FacebookOAuthClient facebookOAuthClient;
    private final OAuthProperties oAuthProperties;
    private final TransactionTemplate transactionTemplate;

    /** users.name 的欄位長度 */
    private static final int NAME_MAX_LENGTH = 50;

    public UserService(UserRepository userRepository,
                       UserIdentityRepository userIdentityRepository,
                       CreditPurchaseRepository creditPurchaseRepository,
                       CourseBookingRepository courseBookingRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       GoogleIdTokenVerifier googleIdTokenVerifier,
                       GithubOAuthClient githubOAuthClient,
                       FacebookOAuthClient facebookOAuthClient,
                       OAuthProperties oAuthProperties,
                       TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.courseBookingRepository = courseBookingRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.githubOAuthClient = githubOAuthClient;
        this.facebookOAuthClient = facebookOAuthClient;
        this.oAuthProperties = oAuthProperties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 刻意不加 @Transactional：save 自成一個交易，撞到 email 的 unique 約束時例外才會在當下丟出，
     * 也才能在 catch 裡重查（包在外層交易裡的話，例外要到 commit 才出現，且交易已作廢無法再查）。
     */
    public SignupResponse signup(SignupRequest request) {
        if (request == null
                || !isValidName(request.name())
                || !ValidUtils.isValidString(request.email())
                || !ValidUtils.isValidString(request.password())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        if (!ValidUtils.isValidPassword(request.password())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RULE);
        }

        String email = normalizeEmail(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(User.ROLE_USER);

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // 上面查的時候還沒有，但同 email 的另一個請求搶先寫入了
            if (userRepository.findByEmail(email).isPresent()) {
                throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
            }
            // 不是 email 重複（例如欄位超長），維持原本的處理
            throw ex;
        }
        return SignupResponse.of(saved.getId(), saved.getName());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.email())
                || !ValidUtils.isValidString(request.password())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        // 與 Node 版一致：登入也會先驗密碼格式
        if (!ValidUtils.isValidPassword(request.password())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RULE);
        }

        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.LOGIN_FAILED));

        // 純第三方登入建立的帳號沒有密碼，一律視為登入失敗（共用同一句訊息，不洩漏帳號型態）
        if (user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw ApiException.badRequest(ErrorMessages.LOGIN_FAILED);
        }

        String token = tokenProvider.createToken(user);
        return LoginResponse.of(token, user.getName());
    }

    /**
     * Google 登入：驗證 ID token 後交給 socialLogin 對應帳號。
     * 刻意不加 @Transactional：驗證 token 要連 Google 抓公鑰，不該佔著資料庫連線等網路。
     */
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        if (request == null || !ValidUtils.isValidString(request.credential())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        SocialProfile profile = googleIdTokenVerifier.verify(request.credential().trim());
        // Google 一定會標示 email 是否驗證過，沒驗證過的直接拒絕，不進 socialLogin
        if (!ValidUtils.isValidString(profile.subject())
                || !ValidUtils.isValidString(profile.email())
                || !profile.emailVerified()) {
            throw ApiException.badRequest(ErrorMessages.GOOGLE_VERIFY_FAILED);
        }

        User user = socialLogin(profile);
        String token = tokenProvider.createToken(user);
        return LoginResponse.of(token, user.getName());
    }

    /**
     * GitHub 登入：用授權碼向 GitHub 取得使用者資料後交給 socialLogin 對應帳號。
     * GithubOAuthClient 只會回傳主要且驗證過的 email，所以可以綁定既有帳號。
     */
    public LoginResponse githubLogin(OAuthCodeLoginRequest request) {
        return oauthCodeLogin(request, githubOAuthClient::fetchProfile);
    }

    /**
     * Facebook 登入：流程同 GitHub。差別是 Facebook 不保證 email 驗證過，
     * 所以只能登入已綁定的帳號或建立新帳號，同 email 已有帳號時回 409，不會自動綁定。
     * 這樣建立的帳號 email_verified 為 false，信箱本人之後用 Google 或 GitHub 登入時會被 takeOver。
     */
    public LoginResponse facebookLogin(OAuthCodeLoginRequest request) {
        return oauthCodeLogin(request, facebookOAuthClient::fetchProfile);
    }

    /**
     * 走 authorization code 流程的平台共用。
     * 刻意不加 @Transactional，理由同 googleLogin（fetchProfile 要連平台好幾次）。
     *
     * @param fetchProfile 用 (code, redirectUri) 向平台換回使用者資料；subject 與 email 保證有值
     */
    private LoginResponse oauthCodeLogin(OAuthCodeLoginRequest request,
                                         BiFunction<String, String, SocialProfile> fetchProfile) {
        if (request == null
                || !ValidUtils.isValidString(request.code())
                // 只接受事先登記的 redirect_uri，不讓授權碼被拿去跟別的網址配對
                || !oAuthProperties.isAllowedRedirectUri(request.redirectUri())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        SocialProfile profile = fetchProfile.apply(request.code().trim(), request.redirectUri());

        User user = socialLogin(profile);
        String token = tokenProvider.createToken(user);
        return LoginResponse.of(token, user.getName());
    }

    /**
     * 第三方登入共用的帳號對應：依序以平台身分、email 找帳號，都沒有就建立新帳號。
     * 呼叫端要先確認 subject 與 email 都有值。
     *
     * 以 email 找到的帳號分兩種：信箱驗證過的（users.email_verified）只新增綁定、密碼保留；
     * 沒驗證過的代表建立它的人不一定是信箱本人，由 takeOver 清掉原本的登入方式後才綁定。
     */
    private User socialLogin(SocialProfile profile) {
        return userIdentityRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.subject())
                .map(UserIdentity::getUser)
                .orElseGet(() -> linkOrCreateSocialUser(profile));
    }

    private User linkOrCreateSocialUser(SocialProfile profile) {
        String email = normalizeEmail(profile.email());
        try {
            // 綁定與建立都要寫 user_identities，建立還要先寫 users，包成一個交易避免只寫一半
            return transactionTemplate.execute(status -> {
                // 鎖住該列：接管會改密碼與 token_version，不能與其他寫入交錯
                User user = userRepository.findByEmailForUpdate(email).orElse(null);
                if (user == null) {
                    User created = new User();
                    created.setName(resolveSocialName(profile.name(), email));
                    created.setEmail(email);
                    created.setRole(User.ROLE_USER);
                    created.setEmailVerified(profile.emailVerified());
                    user = userRepository.save(created);
                } else if (!profile.emailVerified()) {
                    // 沒驗證過的 email 不能拿來綁定既有帳號，否則任何人都能用別人的信箱接管帳號
                    throw ApiException.conflict(ErrorMessages.SOCIAL_EMAIL_REGISTERED);
                } else if (userIdentityRepository.existsByUserIdAndProvider(user.getId(), profile.provider())) {
                    // 已綁定的若就是這個平台帳號，代表另一個請求剛好搶先綁好了，直接登入；
                    // 綁的是這個平台的另一個帳號時不覆蓋
                    return userIdentityRepository
                            .findByProviderAndProviderUserId(profile.provider(), profile.subject())
                            .map(UserIdentity::getUser)
                            .orElseThrow(() -> ApiException.conflict(ErrorMessages.EMAIL_TAKEN));
                } else if (!user.isEmailVerified()) {
                    takeOver(user, profile.provider());
                }
                // 信箱驗證過的帳號只新增一筆身分，原本的密碼保留，之後兩種方式都能登入
                userIdentityRepository.save(new UserIdentity(user, profile.provider(), profile.subject()));
                return user;
            });
        } catch (DataIntegrityViolationException ex) {
            // 交易在 commit 時才寫入，所以約束衝突要在交易外面接。
            // 同一個平台帳號的另一個請求搶先寫好了，改用那筆帳號登入，不讓使用者看到 500
            return userIdentityRepository
                    .findByProviderAndProviderUserId(profile.provider(), profile.subject())
                    .map(UserIdentity::getUser)
                    // 撞到的是 email 或別的平台帳號搶先綁定，照一般的重複註冊處理
                    .orElseThrow(() -> ApiException.conflict(ErrorMessages.EMAIL_TAKEN));
        }
    }

    /**
     * 信箱本人第一次用驗證過的平台登入，而這個信箱已經被人用沒驗證過的方式（密碼註冊、Facebook）建了帳號。
     * 建帳號的可能是本人，也可能是冒用信箱的人，兩者無從分辨，所以一律把原本的登入方式清掉：
     * 密碼、既有的第三方綁定、已簽發的 token 都作廢，之後這個帳號只有信箱本人進得來。
     * 帳號裡的其他資料（名稱、角色、購買與報名紀錄）不動。必須在已鎖住該列的交易內呼叫。
     */
    private void takeOver(User user, String provider) {
        user.setPassword(null);
        userIdentityRepository.deleteAllByUserId(user.getId());
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setEmailVerified(true);
        log.info("帳號 {} 的信箱由 {} 登入驗證，已清除原本的登入方式", user.getId(), provider);
    }

    /**
     * 登入後要改 users 的操作共用：鎖住該列，並確認通過驗證之後 token 沒有被作廢。
     * JwtAuthenticationFilter 的檢查與這裡的寫入不在同一個交易，中間帳號可能剛被接管；
     * 少了這一步，接管前送出的請求就能在接管後替無密碼的帳號設定密碼。必須在交易內呼叫。
     */
    private User lockCurrentUser(AuthUser authUser, String notFoundMessage) {
        User user = userRepository.findByIdForUpdate(authUser.getId())
                .orElseThrow(() -> ApiException.badRequest(notFoundMessage));
        if (user.getTokenVersion() != authUser.getTokenVersion()) {
            throw ApiException.unauthorized(ErrorMessages.TOKEN_INVALID);
        }
        return user;
    }

    /** 平台沒給名稱時用 email 的 @ 前段，並截到 users.name 的長度上限 */
    private String resolveSocialName(String socialName, String email) {
        String name = ValidUtils.isValidString(socialName)
                ? socialName.trim()
                : email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
        if (name.length() <= NAME_MAX_LENGTH) {
            return name;
        }
        // 避免把 emoji 等代理對從中間切斷
        int end = Character.isHighSurrogate(name.charAt(NAME_MAX_LENGTH - 1))
                ? NAME_MAX_LENGTH - 1
                : NAME_MAX_LENGTH;
        return name.substring(0, end).trim();
    }

    public ProfileResponse getProfile(User user) {
        return ProfileResponse.of(user.getName(), user.getEmail(), user.getPassword() != null);
    }

    @Transactional
    public UpdateNameResponse updateName(AuthUser authUser, UpdateNameRequest request) {
        if (request == null || !isValidName(request.name())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        User user = lockCurrentUser(authUser, ErrorMessages.UPDATE_USER_PROFILE_FAILED);

        String newName = request.name().trim();
        // openapi 明訂：新名稱與目前名稱相同要回 400，這是規格不是 bug
        if (newName.equals(user.getName())) {
            throw ApiException.badRequest(ErrorMessages.NAME_NOT_CHANGED);
        }

        user.setName(newName);
        userRepository.save(user);
        return UpdateNameResponse.of(newName);
    }

    /**
     * 有密碼的帳號：用舊密碼換新密碼，檢查順序依 openapi。
     * 沒有密碼的帳號（純第三方登入建立，或被 takeOver 清掉密碼）：不需要舊密碼，直接設定，見 setFirstPassword。
     */
    @Transactional
    public void updatePassword(AuthUser authUser, UpdatePasswordRequest request) {
        if (request == null) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        User user = lockCurrentUser(authUser, ErrorMessages.UPDATE_FAILED);
        if (user.getPassword() == null) {
            setFirstPassword(user, authUser, request);
            return;
        }

        if (!ValidUtils.isValidString(request.password())
                || !ValidUtils.isValidString(request.newPassword())
                || !ValidUtils.isValidString(request.confirmNewPassword())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        if (!ValidUtils.isValidPassword(request.password())
                || !ValidUtils.isValidPassword(request.newPassword())
                || !ValidUtils.isValidPassword(request.confirmNewPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RULE);
        }
        if (request.password().equals(request.newPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_SAME_AS_OLD);
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_CONFIRM_MISMATCH);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_WRONG);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /** 無密碼帳號設定密碼：沒有舊密碼可驗，request.password() 不論帶什麼都忽略 */
    private void setFirstPassword(User user, AuthUser authUser, UpdatePasswordRequest request) {
        // 沒有舊密碼把關，改成要求剛登入過：否則偷到 token 的人可以替帳號設一組密碼，
        // 把最多只能用到 token 過期的存取權變成永久的
        Instant issuedAt = authUser.getTokenIssuedAt();
        if (issuedAt == null || issuedAt.isBefore(Instant.now().minus(SET_PASSWORD_LOGIN_WINDOW))) {
            throw ApiException.badRequest(ErrorMessages.SET_PASSWORD_RELOGIN);
        }
        if (!ValidUtils.isValidString(request.newPassword())
                || !ValidUtils.isValidString(request.confirmNewPassword())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        if (!ValidUtils.isValidPassword(request.newPassword())
                || !ValidUtils.isValidPassword(request.confirmNewPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RULE);
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_CONFIRM_MISMATCH);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<CreditPurchaseResponse> getCreditPurchases(UUID userId) {
        return creditPurchaseRepository.findByUserIdWithPackageOrderByPurchaseAtDesc(userId)
                .stream()
                .map(CreditPurchaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserCoursesResponse getBookings(UUID userId) {
        long purchased = creditPurchaseRepository.sumPurchasedCreditsByUserId(userId);
        long used = courseBookingRepository.countByUserIdAndCancelledAtIsNull(userId);

        List<UserCoursesResponse.Booking> bookings =
                courseBookingRepository.findUserBookings(userId)
                        .stream()
                        .map(UserCoursesResponse.Booking::from)
                        .toList();

        return new UserCoursesResponse(purchased - used, used, bookings);
    }

    /**
     * 名稱必填，且去除前後空白後不能超過 users.name 的長度。
     * Node 版沒有這條檢查；這裡補上是因為超長會讓資料庫寫入失敗而回 500。
     */
    private boolean isValidName(String name) {
        if (!ValidUtils.isValidString(name)) {
            return false;
        }
        String trimmed = name.trim();
        // varchar(50) 以字元（code point）計，emoji 等補充字元算一個
        return trimmed.codePointCount(0, trimmed.length()) <= NAME_MAX_LENGTH;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
