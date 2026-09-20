package com.percyku.livefit.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /api/admin/coaches/revenue → data: { total: { revenue, participants, course_count } } */
public record CoachRevenueResponse(Total total) {

    public record Total(
            long revenue,
            long participants,
            @JsonProperty("course_count") long courseCount) {
    }

    public static CoachRevenueResponse of(long revenue, long participants, long courseCount) {
        return new CoachRevenueResponse(new Total(revenue, participants, courseCount));
    }

    public static CoachRevenueResponse empty() {
        return of(0, 0, 0);
    }
}
