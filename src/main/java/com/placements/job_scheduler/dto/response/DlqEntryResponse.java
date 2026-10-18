package com.placements.job_scheduler.dto.response;

import java.time.LocalDateTime;

public record DlqEntryResponse(
        Long id,
        JobRef originalJob,
        QueueRef queue,
        String jobType,
        String payload,
        String failureReason,
        Integer totalAttempts,
        LocalDateTime movedAt) {

    public record JobRef(Long id) {}

    public record QueueRef(Long id, String name) {}
}
