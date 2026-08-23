package com.placements.job_scheduler.dto.response;

import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.enums.RetryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class QueueResponse {
    private Long id;
    private String name;
    private Integer priority;
    private Integer concurrencyLimit;
    private QueueStatus status;
    private Integer maxRetries;
    private RetryType retryType;
    private Integer baseDelaySeconds;
    private LocalDateTime createdAt;
}
