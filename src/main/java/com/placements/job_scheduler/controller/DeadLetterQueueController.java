package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.entity.DeadLetterQueue;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DeadLetterQueueController {

    private final DeadLetterQueueRepository dlqRepository;
    private final JobRepository jobRepository;

    @GetMapping
    public ResponseEntity<List<DeadLetterQueue>> getAll() {
        return ResponseEntity.ok(dlqRepository.findAll());
    }

    @PostMapping("/{id}/retry")
    @Transactional
    public ResponseEntity<Map<String, String>> retry(@PathVariable Long id) {
        DeadLetterQueue dlqEntry = dlqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DLQ entry not found"));

        // Reset the original job and put it back in queue
        Job job = dlqEntry.getOriginalJob();
        job.setStatus(JobStatus.QUEUED);
        job.setRetryCount(0);
        job.setErrorMessage(null);
        job.setNextRunAt(LocalDateTime.now());
        jobRepository.save(job);

        // Remove from DLQ
        dlqRepository.delete(dlqEntry);

        return ResponseEntity.ok(Map.of("message",
                "Job " + job.getId() + " re-queued successfully"));
    }
}