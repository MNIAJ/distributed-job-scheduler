package com.placements.job_scheduler.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {
    @NotBlank
    private String name;
    @Email
    @NotBlank private String email;
    @NotBlank @Size(min = 6) private String password;
}