package com.percyku.livefit.repository;

import com.percyku.livefit.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** 使用者是否已綁定過這個平台的帳號 */
    boolean existsByUserIdAndProvider(UUID userId, String provider);

    /**
     * 刪掉使用者所有的第三方綁定，必須在交易內呼叫。
     * 刻意用一句 DELETE 而不是逐筆 remove：要刪的資料已被另一個交易刪掉時只是影響 0 筆，不會丟例外
     */
    @Modifying
    @Query("delete from UserIdentity i where i.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
