package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.RetryPolicy;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.enums.RetryType;
import com.placements.job_scheduler.exception.ForbiddenException;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceUnitTest {

    @Mock private JobRepository jobRepository;
    @Mock private TenantAccessService tenantAccessService;

    @InjectMocks private JobService jobService;

    private Queue mockQueue;
    private RetryPolicy mockPolicy;

    @BeforeEach
    void setUp() {
        mockPolicy = RetryPolicy.builder()
                .id(1L)
                .name("default")
                .maxRetries(3)
                .retryType(RetryType.EXPONENTIAL)
                .baseDelaySeconds(30)
                .build();

        mockQueue = Queue.builder()
                .id(1L)
                .name("test-queue")
                .status(QueueStatus.ACTIVE)
                .priority(5)
                .concurrencyLimit(3)
                .retryPolicy(mockPolicy)
                .build();
    }

    @Test
    void shouldCreateJobSuccessfully() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(1L);
        request.setJobType("EMAIL");
        request.setPayload("{\"to\":\"test@test.com\"}");
        request.setPriority(5);

        Job savedJob = Job.builder()
                .id(1L)
                .queue(mockQueue)
                .jobType("EMAIL")
                .payload("{\"to\":\"test@test.com\"}")
                .status(JobStatus.QUEUED)
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .build();

        when(tenantAccessService.requireOwnedQueue(1L)).thenReturn(mockQueue);
        when(jobRepository.save(any(Job.class))).thenReturn(savedJob);

        JobResponse response = jobService.create(request);

        assertNotNull(response);
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals("EMAIL", response.getJobType());
        assertEquals(0, response.getRetryCount());
        verify(jobRepository, times(1)).save(any(Job.class));
    }

    @Test
    void shouldThrowExceptionWhenQueueNotFound() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(999L);
        request.setJobType("EMAIL");

        when(tenantAccessService.requireOwnedQueue(999L))
                .thenThrow(new ResourceNotFoundException("Queue not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> jobService.create(request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldRejectJobForQueueOwnedByAnotherUser() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(1L);
        request.setJobType("EMAIL");

        when(tenantAccessService.requireOwnedQueue(1L))
                .thenThrow(new ForbiddenException("Access denied"));

        assertThrows(ForbiddenException.class,
                () -> jobService.create(request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenQueueIsPaused() {
        mockQueue.setStatus(QueueStatus.PAUSED);
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(1L);
        request.setJobType("EMAIL");

        when(tenantAccessService.requireOwnedQueue(1L)).thenReturn(mockQueue);

        assertThrows(RuntimeException.class,
                () -> jobService.create(request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingJobWhenIdempotencyKeyMatches() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(1L);
        request.setJobType("EMAIL");
        request.setIdempotencyKey("unique-key-abc");

        Job existingJob = Job.builder()
                .id(42L)
                .queue(mockQueue)
                .jobType("EMAIL")
                .status(JobStatus.COMPLETED)
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .idempotencyKey("unique-key-abc")
                .createdAt(LocalDateTime.now())
                .build();

        when(tenantAccessService.requireOwnedQueue(1L)).thenReturn(mockQueue);
        when(jobRepository.findByIdempotencyKey("unique-key-abc"))
                .thenReturn(Optional.of(existingJob));

        JobResponse response = jobService.create(request);

        // returns the existing job, never saves a new one
        assertEquals(42L, response.getId());
        assertEquals(JobStatus.COMPLETED, response.getStatus());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldReturnWinnerWhenIdempotencyRaceHappens() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(1L);
        request.setJobType("EMAIL");
        request.setIdempotencyKey("race-key");

        Job winner = Job.builder()
                .id(77L)
                .queue(mockQueue)
                .jobType("EMAIL")
                .status(JobStatus.QUEUED)
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .idempotencyKey("race-key")
                .createdAt(LocalDateTime.now())
                .build();

        when(tenantAccessService.requireOwnedQueue(1L)).thenReturn(mockQueue);
        // first lookup: nobody has this key yet; after the failed insert: the winner exists
        when(jobRepository.findByIdempotencyKey("race-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(jobRepository.save(any(Job.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        JobResponse response = jobService.create(request);

        assertEquals(77L, response.getId());
    }

    @Test
    void shouldThrowWhenJobNotFound() {
        when(tenantAccessService.requireOwnedJob(999L))
                .thenThrow(new ResourceNotFoundException("Job not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> jobService.getById(999L));
    }

    @Test
    void shouldNotCancelRunningJob() {
        Job runningJob = Job.builder()
                .id(1L)
                .queue(mockQueue)
                .status(JobStatus.RUNNING)
                .jobType("EMAIL")
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .build();

        when(tenantAccessService.requireOwnedJob(1L)).thenReturn(runningJob);

        assertThrows(RuntimeException.class,
                () -> jobService.cancel(1L));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldNotCancelCompletedJob() {
        Job done = Job.builder().id(2L).queue(mockQueue).status(JobStatus.COMPLETED)
                .jobType("EMAIL").priority(5).retryCount(0).maxRetries(3).build();
        when(tenantAccessService.requireOwnedJob(2L)).thenReturn(done);

        assertThrows(RuntimeException.class, () -> jobService.cancel(2L));
        verify(jobRepository, never()).cancelIfQueued(any(), any(), any());
    }

    @Test
    void shouldCancelQueuedJob() {
        Job queued = Job.builder().id(3L).queue(mockQueue).status(JobStatus.QUEUED)
                .jobType("EMAIL").priority(5).retryCount(0).maxRetries(3).build();
        when(tenantAccessService.requireOwnedJob(3L)).thenReturn(queued);
        when(jobRepository.cancelIfQueued(3L, JobStatus.DEAD, JobStatus.QUEUED)).thenReturn(1);

        JobResponse response = jobService.cancel(3L);

        assertEquals(JobStatus.DEAD, response.getStatus());
    }

    @Test
    void shouldNotCancelJobClaimedByWorkerMeanwhile() {
        Job queued = Job.builder().id(4L).queue(mockQueue).status(JobStatus.QUEUED)
                .jobType("EMAIL").priority(5).retryCount(0).maxRetries(3).build();
        when(tenantAccessService.requireOwnedJob(4L)).thenReturn(queued);
        when(jobRepository.cancelIfQueued(4L, JobStatus.DEAD, JobStatus.QUEUED)).thenReturn(0);

        assertThrows(RuntimeException.class, () -> jobService.cancel(4L));
    }

    @Test
    void shouldNotCancelAnotherUsersJob() {
        when(tenantAccessService.requireOwnedJob(1L))
                .thenThrow(new ForbiddenException("Access denied"));

        assertThrows(ForbiddenException.class,
                () -> jobService.cancel(1L));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldListOnlyCurrentUsersJobsByStatus() {
        Job job = Job.builder()
                .id(5L)
                .queue(mockQueue)
                .status(JobStatus.QUEUED)
                .jobType("EMAIL")
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .build();
        Pageable pageable = PageRequest.of(0, 20);

        when(tenantAccessService.currentUserId()).thenReturn(7L);
        when(jobRepository.findByStatusForUser(JobStatus.QUEUED, 7L, pageable))
                .thenReturn(new PageImpl<>(List.of(job)));

        Page<JobResponse> page = jobService.getByStatusPaged(JobStatus.QUEUED, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals(5L, page.getContent().get(0).getId());
        // the unfiltered query must never be used
        verify(jobRepository, never()).findByStatus(any(JobStatus.class), any(Pageable.class));
    }
}