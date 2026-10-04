package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.user.CreditPurchaseResponse;
import com.percyku.livefit.dto.user.GoogleLoginRequest;
import com.percyku.livefit.dto.user.LoginRequest;
import com.percyku.livefit.dto.user.LoginResponse;
import com.percyku.livefit.dto.user.ProfileResponse;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.dto.user.SignupResponse;
import com.percyku.livefit.dto.user.UpdateNameRequest;
import com.percyku.livefit.dto.user.UpdateNameResponse;
import com.percyku.livefit.dto.user.UpdatePasswordRequest;
import com.percyku.livefit.dto.user.UserCoursesResponse;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CreditPurchaseRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.security.GoogleIdTokenVerifier;
import com.percyku.livefit.security.GoogleIdTokenVerifier.GoogleProfile;
import com.percyku.livefit.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/users.js 的業務邏輯，另加 Node 版沒有的 Google 登入 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CourseBookingRepository courseBookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    /** users.name 的欄位長度 */
    private static final int NAME_MAX_LENGTH = 50;

    public UserService(UserRepository userRepository,
                       CreditPurchaseRepository creditPurchaseRepository,
                       CourseBookingRepository courseBookingRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       GoogleIdTokenVerifier googleIdTokenVerifier) {
        this.userRepository = userRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.courseBookingRepository = courseBookingRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.name())
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

        User saved = userRepository.save(user);
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

        // 純 Google 註冊的帳號沒有密碼，一律視為登入失敗（共用同一句訊息，不洩漏帳號型態）
        if (user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw ApiException.badRequest(ErrorMessages.LOGIN_FAILED);
        }

        String token = tokenProvider.createToken(user.getId(), user.getRole());
        return LoginResponse.of(token, user.getName());
    }

    /**
     * Google 登入：驗證 ID token 後依序以 google_sub、email 對應帳號，都沒有就建立新帳號。
     * 刻意不加 @Transactional：驗證 token 要連 Google 抓公鑰，不該佔著資料庫連線等網路；
     * 後面每個 repository 呼叫各自是一個交易，重複寫入由 email / google_sub 的 unique 約束擋下。
     */
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        if (request == null || !ValidUtils.isValidString(request.credential())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        GoogleProfile profile = googleIdTokenVerifier.verify(request.credential().trim());
        // 沒驗證過的 email 不能拿來綁定既有帳號，否則任何人都能用別人的信箱接管帳號
        if (!ValidUtils.isValidString(profile.sub())
                || !ValidUtils.isValidString(profile.email())
                || !profile.emailVerified()) {
            throw ApiException.badRequest(ErrorMessages.GOOGLE_VERIFY_FAILED);
        }

        User user = userRepository.findByGoogleSub(profile.sub())
                .orElseGet(() -> linkOrCreateGoogleUser(profile));

        String token = tokenProvider.createToken(user.getId(), user.getRole());
        return LoginResponse.of(token, user.getName());
    }

    private User linkOrCreateGoogleUser(GoogleProfile profile) {
        String email = normalizeEmail(profile.email());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            // 同一個 email 已綁定另一個 Google 帳號時不覆蓋
            if (user.getGoogleSub() != null) {
                throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
            }
            // 綁定只寫入 google_sub，原本的密碼保留，之後兩種方式都能登入
            user.setGoogleSub(profile.sub());
            return userRepository.save(user);
        }

        User created = new User();
        created.setName(resolveGoogleName(profile.name(), email));
        created.setEmail(email);
        created.setGoogleSub(profile.sub());
        created.setRole(User.ROLE_USER);
        return userRepository.save(created);
    }

    /** Google 沒給名稱時用 email 的 @ 前段，並截到 users.name 的長度上限 */
    private String resolveGoogleName(String googleName, String email) {
        String name = ValidUtils.isValidString(googleName)
                ? googleName.trim()
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
        if (request == null || !ValidUtils.isValidString(request.name())) {
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
            throw ApiException.badRequest(ErrorMessages.GOOGLE_ACCOUNT_NO_PASSWORD);
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
