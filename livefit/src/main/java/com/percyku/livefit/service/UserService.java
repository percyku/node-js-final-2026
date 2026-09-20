package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.user.CreditPurchaseResponse;
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
import com.percyku.livefit.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 對應 backend/controllers/users.js 的業務邏輯 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CourseBookingRepository courseBookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public UserService(UserRepository userRepository,
                       CreditPurchaseRepository creditPurchaseRepository,
                       CourseBookingRepository courseBookingRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.courseBookingRepository = courseBookingRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
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

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw ApiException.badRequest(ErrorMessages.LOGIN_FAILED);
        }

        String token = tokenProvider.createToken(user.getId(), user.getRole());
        return LoginResponse.of(token, user.getName());
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
