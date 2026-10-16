package com.placements.job_scheduler.util;

import com.placements.job_scheduler.enums.RetryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryBackoffTest {

    @Test
    void fixedAlwaysReturnsBase() {
        assertEquals(30, RetryBackoff.delaySeconds(RetryType.FIXED, 30, 1));
        assertEquals(30, RetryBackoff.delaySeconds(RetryType.FIXED, 30, 3));
    }

    @Test
    void linearGrowsByBaseEachAttempt() {
        assertEquals(30, RetryBackoff.delaySeconds(RetryType.LINEAR, 30, 1));
        assertEquals(60, RetryBackoff.delaySeconds(RetryType.LINEAR, 30, 2));
        assertEquals(90, RetryBackoff.delaySeconds(RetryType.LINEAR, 30, 3));
    }

    @Test
    void exponentialDoublesEachAttempt() {
        assertEquals(30, RetryBackoff.delaySeconds(RetryType.EXPONENTIAL, 30, 1));
        assertEquals(60, RetryBackoff.delaySeconds(RetryType.EXPONENTIAL, 30, 2));
        assertEquals(120, RetryBackoff.delaySeconds(RetryType.EXPONENTIAL, 30, 3));
        assertEquals(240, RetryBackoff.delaySeconds(RetryType.EXPONENTIAL, 30, 4));
    }

    @Test
    void delayIsCappedAtOneDay() {
        assertEquals(86_400, RetryBackoff.delaySeconds(RetryType.EXPONENTIAL, 30, 40));
    }
}