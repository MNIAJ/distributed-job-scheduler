package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.ok(jobService.create(request));
    }

    @GetMapping("/queue/{queueId}")
    public ResponseEntity<List<JobResponse>> getByQueue(@PathVariable Long queueId) {
        return ResponseEntity.ok(jobService.getByQueue(queueId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getById(id));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<JobResponse>> getByStatus(
            @PathVariable JobStatus status) {
        return ResponseEntity.ok(jobService.getByStatus(status));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<JobResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.cancel(id));
    }
}