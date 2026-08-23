package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.*;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.RetryType;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    private final JobLogRepository jobLogRepository;
    private final JobEventPublisher jobEventPublisher;

    // Called every 5 seconds — each worker thread tries to claim and run one job
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollAndExecute() {

        if (shuttingDown) return;

        // Get all idle workers
        List<Worker> idleWorkers = workerRepository.findByStatus(WorkerStatus.IDLE);

        for (Worker worker : idleWorkers) {
            // Try to claim a job for this worker
            Optional<Job> claimed = claimJob(worker);
            claimed.ifPresent(job -> executeJob(job, worker));
        }
    }

    // ATOMIC — this is where the magic happens
    private Optional<Job> claimJob(Worker worker) {
        Optional<Job> jobOpt = jobRepository.claimNextJob();

        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        Job job = jobOpt.get();

        // Transition: QUEUED → RUNNING
        job.setStatus(JobStatus.RUNNING);
        job.setClaimedBy(worker);
        job.setClaimedAt(LocalDateTime.now());
        jobRepository.save(job);

        // Mark worker as busy
        worker.setStatus(WorkerStatus.BUSY);
        workerRepository.save(worker);

        jobEventPublisher.publishWorkerUpdate(worker);
        jobEventPublisher.publishJobUpdate(job);

        logJobEvent(job, worker,
                "Job claimed by " + worker.getName(), "INFO");

//        log.info("Worker [{}] claimed job [{}] of type [{}]",
//                worker.getName(), job.getId(), job.getJobType());

        return Optional.of(job);
    }

    private void executeJob(Job job, Worker worker) {
        // Record this execution attempt
        JobExecution execution = JobExecution.builder()
                .job(job)
                .worker(worker)
                .attemptNumber(job.getRetryCount() + 1)
                .status(JobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();
        jobExecutionRepository.save(execution);

        try {
            // Simulate actual job execution
            // In a real system, this would dispatch to job handlers
            // based on jobType (strategy pattern)
            simulateJobExecution(job);

            // SUCCESS — mark completed
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            jobRepository.save(job);

            execution.setStatus(JobStatus.COMPLETED);
            execution.setFinishedAt(LocalDateTime.now());
            jobExecutionRepository.save(execution);

            jobEventPublisher.publishJobUpdate(job);
            jobEventPublisher.publishWorkerUpdate(worker);

            logJobEvent(job, worker,
                    "Job completed successfully", "INFO");

//            log.info("Job [{}] completed successfully by worker [{}]",
//                    job.getId(), worker.getName());

        } catch (Exception e) {
            log.error("Job [{}] failed: {}", job.getId(), e.getMessage());
            handleFailure(job, worker, execution, e.getMessage());

        } finally {
            // Always free the worker, success or failure
            worker.setStatus(WorkerStatus.IDLE);
            worker.setLastHeartbeatAt(LocalDateTime.now());
            workerRepository.save(worker);
        }
    }

    private void simulateJobExecution(Job job) throws Exception {
        // This simulates real work taking time
        // Replace this with real job handlers later
        log.info("Executing job type [{}] with payload: {}",
                job.getJobType(), job.getPayload());

        // Simulate random failures (20% chance) so we can test retry logic
        if (Math.random() < 0.2) {
            throw new Exception("Simulated job failure for testing");
        }

        // Simulate work taking 1-3 seconds
        Thread.sleep((long)(Math.random() * 2000) + 1000);
    }

    private void handleFailure(Job job, Worker worker,
                               JobExecution execution, String errorMessage) {
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(errorMessage);

        execution.setStatus(JobStatus.FAILED);
        execution.setFinishedAt(LocalDateTime.now());
        execution.setErrorMessage(errorMessage);
        jobExecutionRepository.save(execution);

        jobEventPublisher.publishJobUpdate(job);

        if (job.getRetryCount() >= job.getMaxRetries()) {
            // Max retries exceeded — send to Dead Letter Queue
            moveToDeadLetterQueue(job, errorMessage);
        } else {
            // Schedule retry with backoff delay
            LocalDateTime nextRun = calculateNextRunAt(job);
            job.setStatus(JobStatus.QUEUED); // back to queue for retry
            job.setNextRunAt(nextRun);
            jobRepository.save(job);

            log.info("Job [{}] scheduled for retry #{} at {}",
                    job.getId(), job.getRetryCount(), nextRun);
        }
    }

    private LocalDateTime calculateNextRunAt(Job job) {
        Queue queue = job.getQueue();
        int attempt = job.getRetryCount();
        int baseDelay = queue.getRetryPolicy() != null ? queue.getRetryPolicy().getBaseDelaySeconds() : 30;

        long delaySeconds = switch (queue.getRetryPolicy() != null ? queue.getRetryPolicy().getRetryType() : RetryType.EXPONENTIAL) {
            case FIXED -> baseDelay;
            case LINEAR -> (long) baseDelay * attempt;
            case EXPONENTIAL -> (long) baseDelay * (long) Math.pow(2, attempt - 1);
        };

        log.info("Job [{}] retry delay: {} seconds (strategy: {})",
                job.getId(), delaySeconds, queue.getRetryPolicy() != null ? queue.getRetryPolicy().getRetryType() : RetryType.EXPONENTIAL);

        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    private void moveToDeadLetterQueue(Job job, String failureReason) {
        DeadLetterQueue dlqEntry = DeadLetterQueue.builder()
                .originalJob(job)
                .queue(job.getQueue())
                .jobType(job.getJobType())
                .payload(job.getPayload())
                .failureReason(failureReason)
                .totalAttempts(job.getRetryCount())
                .build();

        deadLetterQueueRepository.save(dlqEntry);

        job.setStatus(JobStatus.DEAD);
        jobRepository.save(job);

        log.warn("Job [{}] moved to Dead Letter Queue after {} attempts",
                job.getId(), job.getRetryCount());
    }

    private void logJobEvent(Job job, Worker worker,
                             String message, String level) {
        JobLog log = JobLog.builder()
                .job(job)
                .worker(worker)
                .message(message)
                .logLevel(level)
                .build();
        jobLogRepository.save(log);
    }

    private volatile boolean shuttingDown = false;

    @PreDestroy
    public void onShutdown() {
        shuttingDown = true;
        log.info("Graceful shutdown initiated — stopping job polling");

        long waitUntil = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < waitUntil) {
            long running = workerRepository
                    .countByStatus(WorkerStatus.BUSY);
            if (running == 0) break;
            log.info("Waiting for {} running jobs to finish...", running);
            try { Thread.sleep(2000); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Graceful shutdown complete");
    }
}
