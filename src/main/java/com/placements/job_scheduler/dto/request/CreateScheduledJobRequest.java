package com.placements.job_scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateScheduledJobRequest {
    @NotNull
    private Long queueId;
    @NotBlank
    private String jobType;
    private String payload;
    private Integer priority = 5;
    @NotBlank private String cronExpression;
}