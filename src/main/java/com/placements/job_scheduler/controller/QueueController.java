package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.request.CreateQueueRequest;
import com.placements.job_scheduler.dto.response.QueueResponse;
import com.placements.job_scheduler.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping
    public ResponseEntity<QueueResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateQueueRequest request) {
        return ResponseEntity.ok(queueService.create(projectId, request));
    }

    @GetMapping
    public ResponseEntity<List<QueueResponse>> getAll(@PathVariable Long projectId) {
        return ResponseEntity.ok(queueService.getAll(projectId));
    }

    @PutMapping("/{queueId}/pause")
    public ResponseEntity<QueueResponse> pause(@PathVariable Long queueId) {
        return ResponseEntity.ok(queueService.pause(queueId));
    }

    @PutMapping("/{queueId}/resume")
    public ResponseEntity<QueueResponse> resume(@PathVariable Long queueId) {
        return ResponseEntity.ok(queueService.resume(queueId));
    }

    @DeleteMapping("/{queueId}")
    public ResponseEntity<Void> delete(@PathVariable Long queueId) {
        queueService.delete(queueId);
        return ResponseEntity.noContent().build();
    }
}
