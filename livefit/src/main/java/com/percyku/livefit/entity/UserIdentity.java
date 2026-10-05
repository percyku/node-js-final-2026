package com.percyku.livefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 使用者綁定的第三方登入身分，table: user_identities（Node 版沒有這張表）。
 * 一個平台帳號只能對應一個使用者，一個使用者在同一個平台也只能綁一個帳號。
 */
@Entity
@Table(name = "user_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_identities_provider_subject",
                columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uk_user_identities_user_provider",
                columnNames = {"user_id", "provider"})
})
public class UserIdentity {

    // 刻意用字串常數而非 enum：Hibernate 會替 enum 欄位建 check 約束，
    // 而 ddl-auto=update 之後不會更新它，新增平台時寫入會失敗
    public static final String PROVIDER_GOOGLE = "GOOGLE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "provider", length = 20, nullable = false, updatable = false)
    private String provider;

    /** 該平台的使用者唯一識別碼（Google 是 ID token 的 sub） */
    @Column(name = "provider_user_id", length = 255, nullable = false, updatable = false)
    private String providerUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public UserIdentity() {
    }

    public UserIdentity(User user, String provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
