package com.percyku.livefit.security;

import com.percyku.livefit.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 對應 Node 版 isAuth 掛到 req.user 的完整 User。
 * 角色取自資料庫當下的 role 欄位，而非 token 內的 role（與 Node 版行為一致）。
 */
public class AuthUser implements UserDetails {

    private final User user;
    private final Instant tokenIssuedAt;

    public AuthUser(User user, Instant tokenIssuedAt) {
        this.user = user;
        this.tokenIssuedAt = tokenIssuedAt;
    }

    public User getUser() {
        return user;
    }

    public UUID getId() {
        return user.getId();
    }

    /** 通過驗證當下的 users.token_version（與 token 的 ver 相同），寫入前要在交易內再比對一次 */
    public int getTokenVersion() {
        return user.getTokenVersion();
    }

    /** token 的簽發時間，也就是這次登入的時間；token 沒有 iat 時為 null */
    public Instant getTokenIssuedAt() {
        return tokenIssuedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
