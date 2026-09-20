package com.percyku.livefit.repository;

import com.percyku.livefit.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByUserId(UUID userId);

    Optional<Course> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserId(UUID userId);

    /** 教練的未結束課程（公開頁） */
    @Query("SELECT c FROM Course c JOIN FETCH c.skill WHERE c.userId = :userId AND c.endAt > :now")
    List<Course> findNotEndedByUserId(UUID userId, Instant now);

    /** 全站「進行中」課程：start_at <= now AND end_at > now */
    @Query("SELECT c FROM Course c JOIN FETCH c.user JOIN FETCH c.skill "
            + "WHERE c.startAt <= :now AND c.endAt > :now")
    List<Course> findOngoing(Instant now);

    @Query("SELECT c FROM Course c JOIN FETCH c.skill WHERE c.id = :id AND c.userId = :userId")
    Optional<Course> findByIdAndUserIdWithSkill(UUID id, UUID userId);
}
