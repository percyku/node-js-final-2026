package com.percyku.livefit.repository;

import com.percyku.livefit.entity.Coach;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachRepository extends JpaRepository<Coach, UUID> {

    Optional<Coach> findByUserId(UUID userId);

    @Query("SELECT c FROM Coach c JOIN FETCH c.user")
    List<Coach> findAllWithUser(Pageable pageable);

    @Query("SELECT c FROM Coach c JOIN FETCH c.user WHERE c.id = :id")
    Optional<Coach> findByIdWithUser(UUID id);
}
