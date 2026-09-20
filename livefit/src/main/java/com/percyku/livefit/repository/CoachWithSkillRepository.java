package com.percyku.livefit.repository;

import com.percyku.livefit.entity.CoachWithSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CoachWithSkillRepository extends JpaRepository<CoachWithSkill, UUID> {

    List<CoachWithSkill> findByCoachId(UUID coachId);

    @Query("SELECT cws FROM CoachWithSkill cws JOIN FETCH cws.skill WHERE cws.coachId = :coachId")
    List<CoachWithSkill> findByCoachIdWithSkill(UUID coachId);

    void deleteByCoachId(UUID coachId);
}
