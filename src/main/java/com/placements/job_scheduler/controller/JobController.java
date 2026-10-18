package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.request.CreateScheduledJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.dto.response.JobLogResponse;
import com.placements.job_scheduler.dto.response.ScheduledJobResponse;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.service.CronSchedulerService;
import com.placements.job_scheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final CronSchedulerService cronSchedulerService;

    @PostMapping("/scheduled")
    public ResponseEntity<ScheduledJobResponse> createScheduled(
            @Valid @RequestBody CreateScheduledJobRequest request) {
        return ResponseEntity.ok(cronSchedulerService.create(request));
    }

    @GetMapping("/scheduled")
    public ResponseEntity<List<ScheduledJobResponse>> getAllScheduled() {
        return ResponseEntity.ok(cronSchedulerService.getAllForCurrentUser());
    }

    @PutMapping("/scheduled/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateScheduled(
            @PathVariable Long id) {
        cronSchedulerService.deactivate(id);
        return ResponseEntity.ok(
                Map.of("message", "Scheduled job deactivated"));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<JobLogResponse>> getJobLogs(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getLogs(id));
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.ok(jobService.create(request));
    }

    @GetMapping("/queue/{queueId}")
    public ResponseEntity<Page<JobResponse>> getByQueue(
            @PathVariable Long queueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                jobService.getByQueuePaged(queueId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getById(id));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<JobResponse>> getByStatus(
            @PathVariable JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(
                jobService.getByStatusPaged(status, pageable));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<JobResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.cancel(id));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<JobResponse>> createBatch(
            @Valid @RequestBody List<CreateJobRequest> requests) {
        List<JobResponse> responses = requests.stream()
                .map(jobService::create)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}