package com.placements.job_scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateJobRequest {
    @NotNull
    private Long queueId;
    @NotBlank
    private String jobType;
    private String payload;
    private Integer priority = 5;
    private LocalDateTime scheduledAt; // null = run immediately
    private String idempotencyKey;
}
