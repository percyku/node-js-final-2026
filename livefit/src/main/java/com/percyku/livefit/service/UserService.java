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
import com.percyku.livefit.security.GithubOAuthClient;
import com.percyku.livefit.security.GoogleIdTokenVerifier;
import com.percyku.livefit.security.JwtTokenProvider;
import com.percyku.livefit.security.SocialProfile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/users.js 的業務邏輯，另加 Node 版沒有的第三方登入 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CourseBookingRepository courseBookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GithubOAuthClient githubOAuthClient;
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

        String token = tokenProvider.createToken(user.getId(), user.getRole());
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
        String token = tokenProvider.createToken(user.getId(), user.getRole());
        return LoginResponse.of(token, user.getName());
    }

    /**
     * GitHub 登入：用授權碼向 GitHub 取得使用者資料後交給 socialLogin 對應帳號。
     * 刻意不加 @Transactional，理由同 googleLogin（這裡要連 GitHub 三次）。
     */
    public LoginResponse githubLogin(OAuthCodeLoginRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.code())
                // 只接受事先登記的 redirect_uri，不讓授權碼被拿去跟別的網址配對
                || !oAuthProperties.isAllowedRedirectUri(request.redirectUri())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        // GithubOAuthClient 只會回傳主要且驗證過的 email，沒有的話在裡面就擋掉了
        SocialProfile profile = githubOAuthClient.fetchProfile(request.code().trim(), request.redirectUri());

        User user = socialLogin(profile);
        String token = tokenProvider.createToken(user.getId(), user.getRole());
        return LoginResponse.of(token, user.getName());
    }

    /**
     * 第三方登入共用的帳號對應：依序以平台身分、email 找帳號，都沒有就建立新帳號。
     * 呼叫端要先確認 subject 與 email 都有值。
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
                User user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    User created = new User();
                    created.setName(resolveSocialName(profile.name(), email));
                    created.setEmail(email);
                    created.setRole(User.ROLE_USER);
                    user = userRepository.save(created);
                } else if (!profile.emailVerified()) {
                    // 沒驗證過的 email 不能拿來綁定既有帳號，否則任何人都能用別人的信箱接管帳號
                    throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
                } else if (userIdentityRepository.existsByUserIdAndProvider(user.getId(), profile.provider())) {
                    // 已綁定的若就是這個平台帳號，代表另一個請求剛好搶先綁好了，直接登入；
                    // 綁的是這個平台的另一個帳號時不覆蓋
                    return userIdentityRepository
                            .findByProviderAndProviderUserId(profile.provider(), profile.subject())
                            .map(UserIdentity::getUser)
                            .orElseThrow(() -> ApiException.conflict(ErrorMessages.EMAIL_TAKEN));
                }
                // 綁定只新增一筆身分，原本的密碼保留，之後兩種方式都能登入
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
        return ProfileResponse.of(user.getName(), user.getEmail());
    }

    @Transactional
    public UpdateNameResponse updateName(UUID userId, UpdateNameRequest request) {
        if (request == null || !isValidName(request.name())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.UPDATE_USER_PROFILE_FAILED));

        String newName = request.name().trim();
        // openapi 明訂：新名稱與目前名稱相同要回 400，這是規格不是 bug
        if (newName.equals(user.getName())) {
            throw ApiException.badRequest(ErrorMessages.NAME_NOT_CHANGED);
        }

        user.setName(newName);
        userRepository.save(user);
        return UpdateNameResponse.of(newName);
    }

    @Transactional
    public void updatePassword(UUID userId, UpdatePasswordRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.password())
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.UPDATE_FAILED));

        if (user.getPassword() == null) {
            throw ApiException.badRequest(ErrorMessages.SOCIAL_ACCOUNT_NO_PASSWORD);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_WRONG);
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
