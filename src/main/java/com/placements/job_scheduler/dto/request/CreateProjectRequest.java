package com.placements.job_scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProjectRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String description;
}
