package com.percyku.livefit.repository;

import com.percyku.livefit.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // 下面兩個會鎖住該列（SELECT ... FOR UPDATE），必須在交易內呼叫。
    // Hibernate 更新時是整列寫回，「讀出、修改、寫回 users」的地方都要用它們，
    // 否則與第三方登入的接管同時發生時，會把剛清掉的密碼與舊的 token_version 寫回去

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
