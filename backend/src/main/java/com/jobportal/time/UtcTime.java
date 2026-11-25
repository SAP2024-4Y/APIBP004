package com.jobportal.time;

import com.jobportal.exception.ValidationException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Database LocalDateTime values represent UTC, never the JVM default timezone. */
public final class UtcTime {
    private UtcTime() {}
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    public static LocalDateTime parse(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try { return LocalDateTime.parse(value); }
            catch (DateTimeParseException invalid) {
                throw new ValidationException("Timestamp must be ISO 8601 with Z or an offset; offset-free input is interpreted as UTC");
            }
        }
    }
    public static String format(LocalDateTime value) {
        return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC));
    }
}
