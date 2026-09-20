package com.percyku.livefit.repository;

import com.percyku.livefit.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByName(String name);

    List<Skill> findAllByOrderByCreatedAtAsc();
}
