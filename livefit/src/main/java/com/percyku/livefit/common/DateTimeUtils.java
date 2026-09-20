package com.percyku.livefit.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 課程時間由前端以字串送入，Node 版直接交給 TypeORM 解析。
 * 這裡接受三種常見寫法，一律視為 UTC，與資料庫的 timestamp without time zone 對齊。
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter SPACE_SEPARATED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeUtils() {
    }

    public static Instant parseOrThrow(String value) {
        if (!ValidUtils.isValidString(value)) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        String text = value.trim();

        // 2026-08-20T10:00:00Z / 帶時區位移
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // 換下一種格式
        }
        // 2026-08-20T10:00:00
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // 換下一種格式
        }
        // 2026-08-20 10:00:00
        try {
            return LocalDateTime.parse(text, SPACE_SEPARATED).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
    }
}
