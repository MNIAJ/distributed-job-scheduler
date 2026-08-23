package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;

    public JobResponse create(CreateJobRequest request) {
        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found"));

        if (queue.getStatus() == QueueStatus.PAUSED) {
            throw new RuntimeException("Queue is paused. Cannot accept new jobs.");
        }

        // Idempotency check — don't create duplicate jobs
        if (request.getIdempotencyKey() != null) {
            Optional<Job> existing = jobRepository
                    .findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
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

        return toResponse(jobRepository.save(job));
    }

    public List<JobResponse> getByQueue(Long queueId) {
        return jobRepository.findByQueueId(queueId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public JobResponse getById(Long id) {
        return toResponse(jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found")));
    }

    public List<JobResponse> getByStatus(JobStatus status) {
        return jobRepository.findByStatus(status)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public JobResponse cancel(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (job.getStatus() == JobStatus.RUNNING) {
            throw new RuntimeException("Cannot cancel a running job");
        }

        job.setStatus(JobStatus.DEAD);
        return toResponse(jobRepository.save(job));
    }

    private JobResponse toResponse(Job j) {
        return new JobResponse(j.getId(), j.getJobType(), j.getPayload(),
                j.getStatus(), j.getPriority(), j.getRetryCount(),
                j.getMaxRetries(), j.getScheduledAt(), j.getNextRunAt(),
                j.getErrorMessage(), j.getCreatedAt(), j.getCompletedAt());
    }

    public Page<JobResponse> getByStatusPaged(
            JobStatus status, Pageable pageable) {
        return jobRepository.findByStatus(status, pageable)
                .map(this::toResponse);
    }

    public Page<JobResponse> getByQueuePaged(
            Long queueId, Pageable pageable) {
        return jobRepository.findByQueueId(queueId, pageable)
                .map(this::toResponse);
    }
}
