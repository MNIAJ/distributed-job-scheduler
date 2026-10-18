package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final JobRepository jobRepository;

    // Job events go ONLY to the user who owns the job (client subscribes to /user/queue/jobs)
    public void publishJobUpdate(Job job) {
        try {
            Long ownerId = jobRepository.findOwnerIdByJobId(job.getId()).orElse(null);
            if (ownerId == null) return;

            Map<String, Object> event = new HashMap<>();
            event.put("jobId", job.getId());
            event.put("status", job.getStatus());
            event.put("jobType", job.getJobType());
            event.put("retryCount", job.getRetryCount());
            event.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(ownerId), "/queue/jobs", (Object) event);
        } catch (Exception e) {
            log.warn("Failed to publish job event: {}", e.getMessage());
        }
    }

    // Workers are shared infrastructure: every authenticated user may see them
    public void publishWorkerUpdate(Worker worker) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("workerId", worker.getId());
            event.put("workerName", worker.getName());
            event.put("status", worker.getStatus());
            event.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/workers", (Object) event);
        } catch (Exception e) {
            log.warn("Failed to publish worker event: {}", e.getMessage());
        }
    }
}
