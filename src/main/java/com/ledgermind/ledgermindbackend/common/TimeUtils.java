package com.ledgermind.ledgermindbackend.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * All user-facing timestamps (transaction times, "today" in prompts,
 * schedules) are IST — the product is India-only and the analytics queries
 * compare these values with BETWEEN, so they must all be produced in the
 * same zone regardless of the server's default zone.
 */
public final class TimeUtils {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private TimeUtils() {}

    public static LocalDateTime nowIst() {
        return LocalDateTime.now(IST);
    }

    public static LocalDate todayIst() {
        return LocalDate.now(IST);
    }
}
