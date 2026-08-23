package com.placements.job_scheduler.dto.response;

import com.placements.job_scheduler.enums.QueueStatus;
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
    private Long retryPolicyId;      // just the ID reference
    private String retryPolicyName;  // for display
    private LocalDateTime createdAt;
}