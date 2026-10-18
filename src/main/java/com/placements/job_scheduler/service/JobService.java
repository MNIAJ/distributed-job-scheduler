package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.dto.response.JobLogResponse;
import com.placements.job_scheduler.entity.JobLog;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.JobLogRepository;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobLogRepository jobLogRepository;
    private final WorkerRepository workerRepository;
    private final TenantAccessService tenantAccessService;

    public JobResponse create(CreateJobRequest request) {
        // 404 if the queue does not exist, 403 if it belongs to someone else
        Queue queue = tenantAccessService.requireOwnedQueue(request.getQueueId());

        if (queue.getStatus() == QueueStatus.PAUSED) {
            throw new RuntimeException("Queue is paused. Cannot accept new jobs.");
        }

        // Idempotency check: don't create duplicate jobs
        if (request.getIdempotencyKey() != null) {
            Optional<Job> existing = jobRepository
                    .findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                // never reveal another user's job through a reused key
                tenantAccessService.assertOwned(existing.get());
                return toResponse(existing.get());
            }
        }

        Job job = Job.builder()
                .queue(queue)
                .jobType(request.getJobType())
                .payload(request.getPayload())
                .priority(request.getPriority())
                .status(JobStatus.QUEUED)
                .retryCount(0)
                .maxRetries(queue.getRetryPolicy() != null
                        ? queue.getRetryPolicy().getMaxRetries() : 3)
                .scheduledAt(request.getScheduledAt())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        try {
            return toResponse(jobRepository.save(job));
        }
        catch (DataIntegrityViolationException e) {
            // Two requests with the same idempotency key raced: the database's
            // unique constraint let only one insert succeed. Return the winner.
            if (request.getIdempotencyKey() != null) {
                Optional<Job> winner = jobRepository
                        .findByIdempotencyKey(request.getIdempotencyKey());
                if (winner.isPresent()) {
                    tenantAccessService.assertOwned(winner.get());
                    return toResponse(winner.get());
                }
            }
            throw e;
        }
    }

    public List<JobResponse> getByQueue(Long queueId) {
        tenantAccessService.requireOwnedQueue(queueId);
        return jobRepository.findByQueueId(queueId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Page<JobResponse> getByQueuePaged(Long queueId, Pageable pageable) {
        tenantAccessService.requireOwnedQueue(queueId);
        return jobRepository.findByQueueId(queueId, pageable)
                .map(this::toResponse);
    }

    public JobResponse getById(Long id) {
        return toResponse(tenantAccessService.requireOwnedJob(id));
    }

    // Only the current user's jobs
    public List<JobResponse> getByStatus(JobStatus status) {
        return jobRepository
                .findByStatusForUser(status, tenantAccessService.currentUserId(),
                        Pageable.unpaged())
                .getContent().stream().map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Page<JobResponse> getByStatusPaged(JobStatus status, Pageable pageable) {
        return jobRepository
                .findByStatusForUser(status, tenantAccessService.currentUserId(), pageable)
                .map(this::toResponse);
    }

    public JobResponse cancel(Long id) {
        Job job = tenantAccessService.requireOwnedJob(id);

        if (job.getStatus() != JobStatus.QUEUED) {
            throw new RuntimeException(
                    "Only queued jobs can be cancelled (job is " + job.getStatus() + ")");
        }

        // Atomic: if a worker claims the job right now, this matches no row
        if (jobRepository.cancelIfQueued(id, JobStatus.DEAD, JobStatus.QUEUED) == 0) {
            throw new RuntimeException("Job was claimed by a worker and can no longer be cancelled");
        }

        job.setStatus(JobStatus.DEAD);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobLogResponse> getLogs(Long jobId) {
        tenantAccessService.requireOwnedJob(jobId);
        return jobLogRepository.findByJobIdOrderByCreatedAtAsc(jobId)
                .stream().map(this::toLogResponse).collect(Collectors.toList());
    }

    private JobLogResponse toLogResponse(JobLog l) {
        return new JobLogResponse(l.getId(), l.getJob().getId(),
                l.getWorker() != null ? l.getWorker().getId() : null,
                l.getWorker() != null ? l.getWorker().getName() : null,
                l.getMessage(), l.getLogLevel(), l.getCreatedAt());
    }

    // Workers are shared infrastructure, but the jobs they run belong to tenants
    public List<JobResponse> getRunningJobsOfWorker(Long workerId) {
        if (!workerRepository.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
        return jobRepository
                .findByWorkerForUser(workerId, JobStatus.RUNNING,
                        tenantAccessService.currentUserId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private JobResponse toResponse(Job j) {
        return new JobResponse(j.getId(), j.getJobType(), j.getPayload(),
                j.getStatus(), j.getPriority(), j.getRetryCount(),
                j.getMaxRetries(), j.getScheduledAt(), j.getNextRunAt(),
                j.getErrorMessage(), j.getCreatedAt(), j.getCompletedAt());
    }
}