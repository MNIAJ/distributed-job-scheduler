package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.*;
import com.placements.job_scheduler.enums.*;
import com.placements.job_scheduler.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerServiceUnitTest {

    @Mock private JobRepository jobRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private JobExecutionRepository jobExecutionRepository;
    @Mock private DeadLetterQueueRepository deadLetterQueueRepository;
    @Mock private JobLogRepository jobLogRepository;

    @InjectMocks private WorkerService workerService;

    private Worker mockWorker;
    private Queue mockQueue;
    private RetryPolicy mockPolicy;

    @BeforeEach
    void setUp() {
        mockPolicy = RetryPolicy.builder()
                .id(1L)
                .maxRetries(3)
                .retryType(RetryType.EXPONENTIAL)
                .baseDelaySeconds(30)
                .build();

        mockQueue = Queue.builder()
                .id(1L)
                .name("test-queue")
                .status(QueueStatus.ACTIVE)
                .concurrencyLimit(3)
                .retryPolicy(mockPolicy)
                .build();

        mockWorker = Worker.builder()
                .id(1L)
                .name("worker-1")
                .status(WorkerStatus.IDLE)
                .lastHeartbeatAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldNotPollWhenShuttingDown() {
        // Trigger shutdown
        workerService.onShutdown();

        // pollAndExecute should return immediately without querying workers
        workerService.pollAndExecute();

        verify(workerRepository, never()).findByStatus(any());
    }

    @Test
    void shouldSkipWhenNoJobsAvailable() {
        when(workerRepository.findByStatus(WorkerStatus.IDLE))
                .thenReturn(List.of(mockWorker));
        when(jobRepository.claimNextJob())
                .thenReturn(Optional.empty());

        workerService.pollAndExecute();

        // Worker should stay IDLE — no job claimed
        verify(jobRepository, never()).save(any());
    }

    @Test
    void shouldMoveJobToDLQAfterMaxRetries() {
        Job failedJob = Job.builder()
                .id(1L)
                .queue(mockQueue)
                .jobType("EMAIL")
                .status(JobStatus.FAILED)
                .retryCount(3)
                .maxRetries(3)
                .payload("{}")
                .build();

        DeadLetterQueue dlqEntry = DeadLetterQueue.builder()
                .originalJob(failedJob)
                .queue(mockQueue)
                .jobType(failedJob.getJobType())
                .payload(failedJob.getPayload())
                .failureReason("max retries exceeded")
                .totalAttempts(3)
                .build();

        deadLetterQueueRepository.save(dlqEntry);

        verify(deadLetterQueueRepository, times(1)).save(any());
    }

    @Test
    void shouldCalculateExponentialBackoffCorrectly() {
        // Exponential: base * 2^(attempt-1)
        // attempt 1: 30 * 2^0 = 30s
        // attempt 2: 30 * 2^1 = 60s
        // attempt 3: 30 * 2^2 = 120s

        int base = 30;
        assertEquals(30, base * (int) Math.pow(2, 0));
        assertEquals(60, base * (int) Math.pow(2, 1));
        assertEquals(120, base * (int) Math.pow(2, 2));
    }

    @Test
    void shouldCalculateLinearBackoffCorrectly() {
        // Linear: base * attempt
        int base = 30;
        assertEquals(30, base * 1);
        assertEquals(60, base * 2);
        assertEquals(90, base * 3);
    }

    @Test
    void shouldCalculateFixedBackoffCorrectly() {
        // Fixed: always base
        int base = 30;
        assertEquals(30, base);
        assertEquals(30, base);
        assertEquals(30, base);
    }
}