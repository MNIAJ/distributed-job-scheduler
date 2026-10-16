package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.repository.WorkerRepository;
import com.placements.job_scheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerRepository workerRepository;
    private final JobService jobService;

    // Workers are shared infrastructure (not tenant data)
    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }

    // ...but the jobs they run belong to tenants, so this is filtered per user
    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<JobResponse>> getJobsByWorker(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getRunningJobsOfWorker(id));
    }
}