package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.admin.AdminUserListResponse;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** M7 管理員後台的使用者列表（Node 版沒有）。升級教練在 AdminCoachService.promoteByAdmin */
@Service
public class AdminUserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER = 10;
    private static final int MAX_PER = 100;

    private static final Set<String> ROLES =
            Set.of(User.ROLE_USER, User.ROLE_COACH, User.ROLE_ADMIN);

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** page、per、role 都可省略；有給但不合法一律 400 */
    @Transactional(readOnly = true)
    public AdminUserListResponse listUsers(String page, String per, String role) {
        int pageInt = parsePositiveInt(page, DEFAULT_PAGE);
        int perInt = parsePositiveInt(per, DEFAULT_PER);
        if (perInt > MAX_PER) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        // 同一批匯入的資料 created_at 可能相同，加上 id 讓分頁順序固定
        Pageable pageable = PageRequest.of(pageInt - 1, perInt,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

        Page<User> result;
        if (ValidUtils.isValidString(role)) {
            String normalized = role.trim().toUpperCase();
            if (!ROLES.contains(normalized)) {
                throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
            }
            result = userRepository.findByRole(normalized, pageable);
        } else {
            result = userRepository.findAll(pageable);
        }
        return AdminUserListResponse.of(result, pageInt, perInt);
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
