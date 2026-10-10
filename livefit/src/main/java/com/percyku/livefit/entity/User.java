package com.percyku.livefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 對應 backend/entities/User.js，table: users（第三方登入的綁定資料在 user_identities） */
@Entity
@Table(name = "users")
public class User {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_COACH = "COACH";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "email", length = 320, nullable = false, unique = true)
    private String email;

    /** 純第三方登入建立的帳號沒有密碼，此時為 null */
    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "role", length = 20, nullable = false)
    private String role = ROLE_USER;

    /**
     * 這個 email 是否確認過屬於本人：Google、GitHub 建立的帳號為 true，密碼註冊與 Facebook 建立的為 false。
     * 驗證過的第三方登入遇到 false 的帳號時會接管它並清掉原本的登入方式（見 UserService.takeOver）。
     * ColumnDefault 是給 ddl-auto=update 替既有資料補值用的，Hibernate 新增時一律明寫欄位值
     */
    @ColumnDefault("false")
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** 寫進 JWT 的 ver；加一就能讓這個帳號已簽發的 token 全部失效 */
    @ColumnDefault("0")
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isCoach() { return ROLE_COACH.equals(role); }
}
