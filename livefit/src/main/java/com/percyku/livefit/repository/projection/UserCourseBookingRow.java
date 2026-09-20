package com.percyku.livefit.repository.projection;

import java.time.Instant;
import java.util.UUID;

/** GET /api/users/courses 的一筆預約紀錄（含課程與教練姓名） */
public interface UserCourseBookingRow {
    UUID getCourseId();
    String getName();
    Instant getStartAt();
    Instant getEndAt();
    String getMeetingUrl();
    String getCoachName();
    Instant getCancelledAt();
}
