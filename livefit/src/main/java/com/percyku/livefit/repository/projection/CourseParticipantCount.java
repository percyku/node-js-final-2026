package com.percyku.livefit.repository.projection;

import java.util.UUID;

/** 教練課程列表的報名人數統計（只計 cancelled_at IS NULL） */
public interface CourseParticipantCount {
    UUID getCourseId();
    Long getParticipants();
}
