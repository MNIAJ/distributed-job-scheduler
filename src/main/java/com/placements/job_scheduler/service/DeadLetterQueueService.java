package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.response.DlqEntryResponse;
import com.placements.job_scheduler.entity.DeadLetterQueue;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final DeadLetterQueueRepository dlqRepository;
    private final JobRepository jobRepository;
    private final TenantAccessService tenantAccessService;

    // Only entries for queues owned by the current user
    @Transactional(readOnly = true)
    public List<DlqEntryResponse> getAllForCurrentUser() {
        return dlqRepository.findAllForUser(tenantAccessService.currentUserId())
                .stream().map(this::toResponse).collect(Collectors.toList());
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

    private DlqEntryResponse toResponse(DeadLetterQueue d) {
        Queue q = d.getQueue();
        return new DlqEntryResponse(
                d.getId(),
                d.getOriginalJob() != null
                        ? new DlqEntryResponse.JobRef(d.getOriginalJob().getId()) : null,
                q != null ? new DlqEntryResponse.QueueRef(q.getId(), q.getName()) : null,
                d.getJobType(),
                d.getPayload(),
                d.getFailureReason(),
                d.getTotalAttempts(),
                d.getMovedAt());
    }
}
