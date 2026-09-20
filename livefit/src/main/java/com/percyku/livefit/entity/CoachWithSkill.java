package com.percyku.livefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 對應 backend/entities/CoachWithSkill.js，table: coach_with_skills */
@Entity
@Table(name = "coach_with_skills")
public class CoachWithSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", insertable = false, updatable = false)
    private Skill skill;

    public CoachWithSkill() {
    }

    public CoachWithSkill(UUID coachId, UUID skillId) {
        this.coachId = coachId;
        this.skillId = skillId;
    }

    public UUID getId() { return id; }
    public UUID getCoachId() { return coachId; }
    public void setCoachId(UUID coachId) { this.coachId = coachId; }
    public UUID getSkillId() { return skillId; }
    public void setSkillId(UUID skillId) { this.skillId = skillId; }
    public Instant getCreatedAt() { return createdAt; }
    public Skill getSkill() { return skill; }
}
