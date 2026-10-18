package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.response.DlqEntryResponse;
import com.placements.job_scheduler.service.DeadLetterQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DeadLetterQueueController {

    private final DeadLetterQueueService dlqService;

    @GetMapping
    public ResponseEntity<List<DlqEntryResponse>> getAll() {
        return ResponseEntity.ok(dlqService.getAllForCurrentUser());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retry(@PathVariable Long id) {
        Long jobId = dlqService.retry(id);
        return ResponseEntity.ok(Map.of("message",
                "Job " + jobId + " re-queued successfully"));
    }
}