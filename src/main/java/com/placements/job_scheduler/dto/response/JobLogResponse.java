package com.placements.job_scheduler.dto.response;

import java.time.LocalDateTime;

public record JobLogResponse(
        Long id,
        Long jobId,
        Long workerId,
        String workerName,
        String message,
        String logLevel,
        LocalDateTime createdAt) {
}
