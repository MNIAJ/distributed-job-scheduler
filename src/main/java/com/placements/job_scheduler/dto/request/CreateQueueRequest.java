package com.placements.job_scheduler.dto.request;

import com.placements.job_scheduler.enums.RetryType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateQueueRequest {
    @NotBlank
    private String name;
    private Integer priority = 5;
    private Integer concurrencyLimit = 5;
    private Integer maxRetries = 3;
    private RetryType retryType = RetryType.EXPONENTIAL;
    private Integer baseDelaySeconds = 30;
}
