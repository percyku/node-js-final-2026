package com.percyku.livefit.dto.admin;

import com.percyku.livefit.entity.User;

/** POST /api/admin/users/admins 的回應，user 的欄位與使用者列表的每一筆相同 */
public record AdminCreatedResponse(AdminUserListResponse.UserItem user) {

    public static AdminCreatedResponse of(User user) {
        return new AdminCreatedResponse(AdminUserListResponse.UserItem.from(user));
    }
}
