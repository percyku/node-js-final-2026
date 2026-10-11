package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.admin.AdminCreatedResponse;
import com.percyku.livefit.dto.admin.AdminUserListResponse;
import com.percyku.livefit.dto.user.SignupRequest;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.security.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** M7 管理員後台：使用者列表、建立管理員、重設管理員密碼（Node 版沒有）。升級教練在 AdminCoachService.promoteByAdmin */
@Service
public class AdminUserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER = 10;
    private static final int MAX_PER = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 重設後的密碼是固定的：到對方自己改掉之前，知道這組密碼的人都能登入那個管理員帳號。
     * 只適合本機或練習環境，對外的環境要改成隨機產生或走信箱驗證
     */
    static final String RESET_PASSWORD = "Admin123";

    private static final Set<String> ROLES =
            Set.of(User.ROLE_USER, User.ROLE_COACH, User.ROLE_ADMIN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** page、per、role、keyword 都可省略；有給但不合法一律 400。keyword 比對名稱或 email */
    @Transactional(readOnly = true)
    public AdminUserListResponse listUsers(String page, String per, String role, String keyword) {
        int pageInt = parsePositiveInt(page, DEFAULT_PAGE);
        int perInt = parsePositiveInt(per, DEFAULT_PER);
        if (perInt > MAX_PER) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        // 同一批匯入的資料 created_at 可能相同，加上 id 讓分頁順序固定
        Pageable pageable = PageRequest.of(pageInt - 1, perInt,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

        String pattern = toLikePattern(keyword);

        Page<User> result;
        if (ValidUtils.isValidString(role)) {
            String normalized = role.trim().toUpperCase();
            if (!ROLES.contains(normalized)) {
                throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
            }
            result = userRepository.searchByRole(normalized, pattern, pageable);
        } else {
            result = userRepository.search(pattern, pageable);
        }
        return AdminUserListResponse.of(result, pageInt, perInt);
    }

    /**
     * 建立管理員。欄位規則與 UserService.signup 相同，差別只在 role。
     * 與預設管理員一樣 email_verified 為 false、只能用密碼登入（第三方登入由 UserService 擋掉）。
     * 刻意不加 @Transactional，理由同 signup：撞到 email 的 unique 約束時要能在 catch 裡重查。
     */
    public AdminCreatedResponse createAdmin(SignupRequest request) {
        if (request == null
                || !ValidUtils.isValidName(request.name())
                || !ValidUtils.isValidString(request.email())
                || !ValidUtils.isValidString(request.password())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        if (!ValidUtils.isValidPassword(request.password())) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RULE);
        }

        // 登入時 email 會轉小寫再查（UserService.normalizeEmail），這裡要存成同樣的形式
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
        }

        User admin = new User();
        admin.setName(request.name().trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setRole(User.ROLE_ADMIN);

        try {
            return AdminCreatedResponse.of(userRepository.saveAndFlush(admin));
        } catch (DataIntegrityViolationException ex) {
            // 上面查的時候還沒有，但同 email 的另一個請求搶先寫入了
            if (userRepository.findByEmail(email).isPresent()) {
                throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
            }
            throw ex;
        }
    }

    /**
     * 把另一位管理員的密碼重設成 RESET_PASSWORD，並作廢他已簽發的 token
     * （忘記密碼也可能是帳號被盜，舊的登入狀態一併清掉）。
     * 只能對管理員用：對一般會員與教練開放的話，管理員就能登入任何人的帳號。
     */
    @Transactional
    public void resetAdminPassword(AuthUser caller, UUID userId) {
        // 重設自己等於把自己登出，而且知道目前密碼的人該走修改密碼
        if (caller.getId().equals(userId)) {
            throw ApiException.badRequest(ErrorMessages.CANNOT_RESET_SELF);
        }

        // 鎖住該列再改：整列寫回，不鎖的話會蓋掉同時發生的其他修改
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.USER_NOT_FOUND));
        if (!target.isAdmin()) {
            throw ApiException.badRequest(ErrorMessages.ONLY_ADMIN_CAN_BE_RESET);
        }

        target.setPassword(passwordEncoder.encode(RESET_PASSWORD));
        target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);
    }

    /** 組成「部分符合、不分大小寫」的 LIKE 字串；使用者輸入的 % 與 _ 當成一般字元 */
    private String toLikePattern(String keyword) {
        if (!ValidUtils.isValidString(keyword)) {
            return "%";
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        String escaped = trimmed.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (!ValidUtils.isValidString(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
    }
}
