package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.RetryPolicy;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.enums.RetryType;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceUnitTest {

    @Mock private JobRepository jobRepository;
    @Mock private QueueRepository queueRepository;

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
        // Arrange
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

        when(queueRepository.findById(1L)).thenReturn(Optional.of(mockQueue));
        when(jobRepository.save(any(Job.class))).thenReturn(savedJob);

        // Act
        JobResponse response = jobService.create(request);

        // Assert
        assertNotNull(response);
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals("EMAIL", response.getJobType());
        assertEquals(0, response.getRetryCount());
        verify(jobRepository, times(1)).save(any(Job.class));
    }

    @Test
    void shouldThrowExceptionWhenQueueNotFound() {
        // Arrange
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(999L);
        request.setJobType("EMAIL");

        when(queueRepository.findById(999L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(ResourceNotFoundException.class,
                () -> jobService.create(request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenQueueIsPaused() {
        // Arrange
        mockQueue.setStatus(QueueStatus.PAUSED);
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(1L);
        request.setJobType("EMAIL");

        when(queueRepository.findById(1L)).thenReturn(Optional.of(mockQueue));

        // Act + Assert
        assertThrows(RuntimeException.class,
                () -> jobService.create(request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingJobWhenIdempotencyKeyMatches() {
        // Arrange
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

        when(queueRepository.findById(1L)).thenReturn(Optional.of(mockQueue));
        when(jobRepository.findByIdempotencyKey("unique-key-abc"))
                .thenReturn(Optional.of(existingJob));

        // Act
        JobResponse response = jobService.create(request);

        // Assert — returns existing job, never saves a new one
        assertEquals(42L, response.getId());
        assertEquals(JobStatus.COMPLETED, response.getStatus());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenJobNotFound() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

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

        when(jobRepository.findById(1L)).thenReturn(Optional.of(runningJob));

        assertThrows(RuntimeException.class,
                () -> jobService.cancel(1L));
        verify(jobRepository, never()).save(any());
    }
}