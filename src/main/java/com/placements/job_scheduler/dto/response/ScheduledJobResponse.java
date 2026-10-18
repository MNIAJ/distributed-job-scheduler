package com.placements.job_scheduler.dto.response;

import java.time.LocalDateTime;

public record ScheduledJobResponse(
        Long id,
        Long queueId,
        String jobType,
        String payload,
        String cronExpression,
        Integer priority,
        Boolean isActive,
        LocalDateTime nextRunAt,
        LocalDateTime lastRunAt,
        LocalDateTime createdAt) {
}
