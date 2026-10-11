package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.percyku.livefit.entity.User;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/admin/users 的回應。
 * 只挑管理頁面用得到的欄位，password 與 token_version 不外流。
 */
public record AdminUserListResponse(List<UserItem> users, Pagination pagination) {

    public record UserItem(
            UUID id,
            String name,
            String email,
            String role,
            @JsonProperty("created_at") Instant createdAt) {
    }

    public record Pagination(
            int page,
            int per,
            long total,
            @JsonProperty("total_pages") int totalPages) {
    }

    public static AdminUserListResponse of(Page<User> result, int page, int per) {
        List<UserItem> users = result.getContent().stream()
                .map(user -> new UserItem(user.getId(), user.getName(), user.getEmail(),
                        user.getRole(), user.getCreatedAt()))
                .toList();
        return new AdminUserListResponse(users,
                new Pagination(page, per, result.getTotalElements(), result.getTotalPages()));
    }
}
