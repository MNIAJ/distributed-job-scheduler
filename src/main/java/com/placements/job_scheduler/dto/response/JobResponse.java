package com.placements.job_scheduler.dto.response;

import com.placements.job_scheduler.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class JobResponse {
    private Long id;
    private String jobType;
    private String payload;
    private JobStatus status;
    private Integer priority;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime scheduledAt;
    private LocalDateTime nextRunAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
