package com.percyku.livefit.repository;

import com.percyku.livefit.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** 使用者是否已綁定過這個平台的帳號 */
    boolean existsByUserIdAndProvider(UUID userId, String provider);
}
