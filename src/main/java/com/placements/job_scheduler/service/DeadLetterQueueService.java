package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.DeadLetterQueue;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final DeadLetterQueueRepository dlqRepository;
    private final JobRepository jobRepository;
    private final TenantAccessService tenantAccessService;

    // Only entries for queues owned by the current user
    public List<DeadLetterQueue> getAllForCurrentUser() {
        return dlqRepository.findAllForUser(tenantAccessService.currentUserId());
    }

    @Transactional
    public Long retry(Long dlqId) {
        DeadLetterQueue entry = tenantAccessService.requireOwnedDlqEntry(dlqId);

        // Reset the original job and put it back in the queue
        Job job = entry.getOriginalJob();
        job.setStatus(JobStatus.QUEUED);
        job.setRetryCount(0);
        job.setErrorMessage(null);
        job.setNextRunAt(LocalDateTime.now());
        jobRepository.save(job);

        // Remove from the DLQ
        dlqRepository.delete(entry);

        return job.getId();
    }
}