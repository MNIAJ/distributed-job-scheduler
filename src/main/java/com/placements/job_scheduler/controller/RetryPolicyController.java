package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.request.CreateRetryPolicyRequest;
import com.placements.job_scheduler.entity.RetryPolicy;
import com.placements.job_scheduler.repository.RetryPolicyRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/retry-policies")
@RequiredArgsConstructor
public class RetryPolicyController {

    private final RetryPolicyRepository retryPolicyRepository;

    @PostMapping
    public ResponseEntity<RetryPolicy> create(
            @Valid @RequestBody CreateRetryPolicyRequest request) {
        RetryPolicy policy = RetryPolicy.builder()
                .name(request.getName())
                .maxRetries(request.getMaxRetries())
                .retryType(request.getRetryType())
                .baseDelaySeconds(request.getBaseDelaySeconds())
                .build();
        return ResponseEntity.ok(retryPolicyRepository.save(policy));
    }

    @GetMapping
    public ResponseEntity<List<RetryPolicy>> getAll() {
        return ResponseEntity.ok(retryPolicyRepository.findAll());
    }
}