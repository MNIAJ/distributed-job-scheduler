package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
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
    private final JobRepository jobRepository;

    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<List<Job>> getJobsByWorker(@PathVariable Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
        return ResponseEntity.ok(
                jobRepository.findByClaimedByAndStatus(worker, JobStatus.RUNNING)
        );
    }
}
