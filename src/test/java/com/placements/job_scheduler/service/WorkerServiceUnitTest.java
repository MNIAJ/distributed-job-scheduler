package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobExecutionRepository;
import com.placements.job_scheduler.repository.JobLogRepository;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerServiceUnitTest {

    @Mock private JobRepository jobRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private JobExecutionRepository jobExecutionRepository;
    @Mock private DeadLetterQueueRepository deadLetterQueueRepository;
    @Mock private JobLogRepository jobLogRepository;
    @Mock private JobEventPublisher jobEventPublisher;
    @Mock private WorkerRegistry workerRegistry;
    @Mock private PlatformTransactionManager transactionManager;

    private WorkerService workerService;

    @BeforeEach
    void setUp() {
        when(workerRegistry.getWorkerCount()).thenReturn(2);
        workerService = new WorkerService(jobRepository, workerRepository,
                jobExecutionRepository, deadLetterQueueRepository, jobLogRepository,
                jobEventPublisher, workerRegistry, transactionManager);
    }

    @Test
    void shouldNotPollWhenShuttingDown() {
        workerService.onShutdown();

        workerService.pollAndExecute();

        verify(workerRepository, never())
                .findByInstanceIdAndStatus(any(String.class), any(WorkerStatus.class));
    }

    @Test
    void shouldDoNothingWhenNoWorkerIsIdle() {
        when(workerRegistry.getInstanceId()).thenReturn("inst-1");
        when(workerRepository.findByInstanceIdAndStatus("inst-1", WorkerStatus.IDLE))
                .thenReturn(List.of());

        workerService.pollAndExecute();

        verify(jobRepository, never()).claimNextJob();
    }

    @Test
    void shouldStopPollingAfterFirstEmptyClaim() {
        Worker w1 = Worker.builder().id(1L).name("w1").status(WorkerStatus.IDLE).build();
        Worker w2 = Worker.builder().id(2L).name("w2").status(WorkerStatus.IDLE).build();

        when(workerRegistry.getInstanceId()).thenReturn("inst-1");
        when(workerRepository.findByInstanceIdAndStatus("inst-1", WorkerStatus.IDLE))
                .thenReturn(List.of(w1, w2));
        when(jobRepository.claimNextJob()).thenReturn(Optional.empty());

        workerService.pollAndExecute();

        // the queue is empty, so the second worker is not even tried
        verify(jobRepository, times(1)).claimNextJob();
        verify(workerRepository, never()).transitionStatus(
                any(Long.class), any(WorkerStatus.class), any(WorkerStatus.class));
    }
}