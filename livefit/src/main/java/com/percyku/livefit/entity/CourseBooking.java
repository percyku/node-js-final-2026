package com.percyku.livefit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 對應 backend/entities/CourseBooking.js，table: course_booking。
 * booking_at 與 created_at 在 TypeORM 都是 createDate，故兩者皆為建立時間。
 * 取消報名為軟刪除：只寫入 cancelled_at。
 */
@Entity
@Table(name = "course_booking")
public class CourseBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @CreationTimestamp
    @Column(name = "booking_at", updatable = false, nullable = false)
    private Instant bookingAt;

    @Column(name = "join_at")
    private Instant joinAt;

    @Column(name = "leave_at")
    private Instant leaveAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public CourseBooking() {
    }

    public CourseBooking(UUID userId, UUID courseId) {
        this.userId = userId;
        this.courseId = courseId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public Instant getBookingAt() { return bookingAt; }
    public Instant getJoinAt() { return joinAt; }
    public Instant getLeaveAt() { return leaveAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
}
