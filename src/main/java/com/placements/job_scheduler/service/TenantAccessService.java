package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.DeadLetterQueue;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Project;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.ScheduledJob;
import com.placements.job_scheduler.exception.ForbiddenException;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import com.placements.job_scheduler.repository.ScheduledJobRepository;
import com.placements.job_scheduler.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that answers: "does this resource belong to the logged-in user?"
 * Ownership chain: Job -> Queue -> Project -> User.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantAccessService {

    private final CurrentUserProvider currentUserProvider;
    private final QueueRepository queueRepository;
    private final JobRepository jobRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final DeadLetterQueueRepository deadLetterQueueRepository;

    public Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    public Queue requireOwnedQueue(Long queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found"));
        assertOwner(queue.getProject());
        return queue;
    }

    public Job requireOwnedJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        assertOwned(job);
        return job;
    }

    public ScheduledJob requireOwnedScheduledJob(Long id) {
        ScheduledJob scheduledJob = scheduledJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled job not found"));
        assertOwner(scheduledJob.getQueue().getProject());
        return scheduledJob;
    }

    public DeadLetterQueue requireOwnedDlqEntry(Long id) {
        DeadLetterQueue entry = deadLetterQueueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DLQ entry not found"));
        Queue queue = (entry.getQueue() != null)
                ? entry.getQueue()
                : entry.getOriginalJob().getQueue();
        assertOwner(queue.getProject());
        return entry;
    }

    public void assertOwned(Job job) {
        assertOwner(job.getQueue().getProject());
    }

    private void assertOwner(Project project) {
        if (!project.getUser().getId().equals(currentUserId())) {
            throw new ForbiddenException("Access denied");
        }
    }
}