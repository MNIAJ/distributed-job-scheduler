package com.placements.job_scheduler.util;

import com.placements.job_scheduler.enums.RetryType;

public final class RetryBackoff {

    private static final long MAX_DELAY_SECONDS = 24 * 60 * 60; // cap at 1 day

    private RetryBackoff() {}

    /**
     * @param attempt 1 for the first retry, 2 for the second, ...
     */
    public static long delaySeconds(RetryType type, int baseDelaySeconds, int attempt) {
        int n = Math.max(attempt, 1);
        long delay = switch (type) {
            case FIXED -> baseDelaySeconds;
            case LINEAR -> (long) baseDelaySeconds * n;
            case EXPONENTIAL -> (long) baseDelaySeconds * (1L << Math.min(n - 1, 20));
        };
        return Math.min(delay, MAX_DELAY_SECONDS);
    }
}