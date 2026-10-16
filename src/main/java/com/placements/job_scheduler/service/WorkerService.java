package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.DeadLetterQueue;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.JobExecution;
import com.placements.job_scheduler.entity.JobLog;
import com.placements.job_scheduler.entity.RetryPolicy;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.RetryType;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobExecutionRepository;
import com.placements.job_scheduler.repository.JobLogRepository;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
import com.placements.job_scheduler.util.RetryBackoff;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class WorkerService {

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    private final JobLogRepository jobLogRepository;
    private final JobEventPublisher jobEventPublisher;
    private final WorkerRegistry workerRegistry;

    // Lets us run small, explicit transactions from plain code
    // (@Transactional does not work when a method calls another method of the same class)
    private final TransactionTemplate tx;

    // One pool thread per worker: jobs really run in parallel
    private final ExecutorService executor;

    private volatile boolean shuttingDown = false;

    // Data handed from the "start" transaction to the execution step
    private record RunContext(Long executionId, String jobType, String payload) {}

    public WorkerService(JobRepository jobRepository,
                         WorkerRepository workerRepository,
                         JobExecutionRepository jobExecutionRepository,
                         DeadLetterQueueRepository deadLetterQueueRepository,
                         JobLogRepository jobLogRepository,
                         JobEventPublisher jobEventPublisher,
                         WorkerRegistry workerRegistry,
                         PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.workerRepository = workerRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.deadLetterQueueRepository = deadLetterQueueRepository;
        this.jobLogRepository = jobLogRepository;
        this.jobEventPublisher = jobEventPublisher;
        this.workerRegistry = workerRegistry;
        this.tx = new TransactionTemplate(transactionManager);

        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "job-worker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(
                workerRegistry.getWorkerCount(), threadFactory);
    }

    // -------------------------------------------------------------------
    // 1. POLLER: claims jobs quickly and hands them to the pool
    // -------------------------------------------------------------------
    @Scheduled(fixedDelay = 2000)
    public void pollAndExecute() {

        if (shuttingDown) return;

        // Only THIS instance's workers
        List<Worker> idleWorkers = workerRepository.findByInstanceIdAndStatus(
                workerRegistry.getInstanceId(), WorkerStatus.IDLE);

        for (Worker worker : idleWorkers) {
            Long workerId = worker.getId();

            Optional<Long> claimedJobId;
            try {
                // short transaction: claim and commit
                claimedJobId = tx.execute(status -> claimJob(workerId));
            } catch (Exception e) {
                log.error("Could not claim a job for worker [{}]: {}",
                        worker.getName(), e.getMessage());
                continue;
            }

            // Nothing eligible right now, so no point trying the other workers
            if (claimedJobId == null || claimedJobId.isEmpty()) break;

            Long jobId = claimedJobId.get();
            publishWorker(workerId);
            publishJob(jobId);

            try {
                executor.submit(() -> runJob(jobId, workerId));
            } catch (RejectedExecutionException e) {
                // Pool is closing: give the job back
                log.warn("Pool is shutting down; returning job [{}] to the queue", jobId);
                jobRepository.requeueRunningJobsOfWorker(workerId, LocalDateTime.now(),
                        JobStatus.QUEUED, JobStatus.RUNNING);
                workerRepository.transitionStatus(workerId,
                        WorkerStatus.BUSY, WorkerStatus.IDLE);
                break;
            }
        }
    }

    // Runs inside a transaction (see pollAndExecute)
    private Optional<Long> claimJob(Long workerId) {
        // FOR UPDATE SKIP LOCKED: only one transaction can get this row
        Optional<Job> jobOpt = jobRepository.claimNextJob();
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        Job job = jobOpt.get();
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new IllegalStateException("Worker not found: " + workerId));

        // QUEUED -> RUNNING
        job.setStatus(JobStatus.RUNNING);
        job.setClaimedBy(worker);
        job.setClaimedAt(LocalDateTime.now());
        jobRepository.save(job);

        logJobEvent(job, worker, "Job claimed by " + worker.getName(), "INFO");

        // IDLE -> BUSY, guarded. If the worker is no longer IDLE we abort,
        // and the whole transaction (including the job update) rolls back.
        int updated = workerRepository.transitionStatus(
                workerId, WorkerStatus.IDLE, WorkerStatus.BUSY);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Worker " + workerId + " is no longer IDLE; abandoning claim");
        }

        return Optional.of(job.getId());
    }

    // -------------------------------------------------------------------
    // 2. RUNNER: executes on a pool thread, with NO transaction open
    // -------------------------------------------------------------------
    private void runJob(Long jobId, Long workerId) {
        try {
            // step 1: create the execution record (short transaction)
            RunContext context = tx.execute(status -> startExecution(jobId, workerId));
            if (context == null) return;

            // step 2: do the actual work outside any transaction
            String error = null;
            try {
                simulateJobExecution(context.jobType(), context.payload());
            } catch (Exception e) {
                error = (e.getMessage() != null) ? e.getMessage() : e.toString();
                log.error("Job [{}] failed: {}", jobId, error);
            }

            // step 3: record the result (short transaction)
            final String errorMessage = error;
            tx.executeWithoutResult(status ->
                    finishExecution(jobId, workerId, context.executionId(), errorMessage));

            publishJob(jobId);

        } catch (Exception e) {
            log.error("Unexpected error while running job [{}]: {}",
                    jobId, e.getMessage(), e);
        } finally {
            freeWorker(workerId);
        }
    }

    private RunContext startExecution(Long jobId, Long workerId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
        Worker worker = workerRepository.getReferenceById(workerId);

        JobExecution execution = JobExecution.builder()
                .job(job)
                .worker(worker)
                .attemptNumber(job.getRetryCount() + 1)
                .status(JobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();
        jobExecutionRepository.save(execution);

        return new RunContext(execution.getId(), job.getJobType(), job.getPayload());
    }

    private void finishExecution(Long jobId, Long workerId,
                                 Long executionId, String errorMessage) {
        JobExecution execution = jobExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Execution not found: " + executionId));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new IllegalStateException("Worker not found: " + workerId));

        // Row lock: completion and recovery can never change this job at the same time
        Job job = jobRepository.findByIdForUpdate(jobId).orElse(null);

        boolean stillOurs = job != null
                && job.getStatus() == JobStatus.RUNNING
                && job.getClaimedBy() != null
                && workerId.equals(job.getClaimedBy().getId());

        if (!stillOurs) {
            // The job was recovered and given to someone else while we were running it.
            log.warn("Job [{}] is no longer owned by worker [{}]; discarding this result",
                    jobId, worker.getName());
            execution.setStatus(JobStatus.FAILED);
            execution.setFinishedAt(LocalDateTime.now());
            execution.setErrorMessage("Result discarded: job was re-assigned while running");
            jobExecutionRepository.save(execution);
            return;
        }

        if (errorMessage == null) {
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            jobRepository.save(job);

            execution.setStatus(JobStatus.COMPLETED);
            execution.setFinishedAt(LocalDateTime.now());
            jobExecutionRepository.save(execution);

            logJobEvent(job, worker, "Job completed successfully", "INFO");
        } else {
            handleFailure(job, worker, execution, errorMessage);
        }
    }

    private void simulateJobExecution(String jobType, String payload) throws Exception {
        // Placeholder for real job handlers (strategy pattern by jobType)
        log.info("Executing job type [{}] with payload: {}", jobType, payload);

        // Random failures (20%) so the retry logic can be tested
        if (Math.random() < 0.2) {
            throw new Exception("Simulated job failure for testing");
        }

        // Simulate work taking 1-3 seconds
        Thread.sleep((long) (Math.random() * 2000) + 1000);
    }

    private void handleFailure(Job job, Worker worker,
                               JobExecution execution, String errorMessage) {
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(errorMessage);
        job.setClaimedBy(null);
        job.setClaimedAt(null);

        execution.setStatus(JobStatus.FAILED);
        execution.setFinishedAt(LocalDateTime.now());
        execution.setErrorMessage(errorMessage);
        jobExecutionRepository.save(execution);

        logJobEvent(job, worker, "Job failed (attempt " + job.getRetryCount()
                + "): " + errorMessage, "ERROR");

        if (job.getRetryCount() >= job.getMaxRetries()) {
            // Max retries exceeded: Dead Letter Queue
            moveToDeadLetterQueue(job, errorMessage);
        } else {
            LocalDateTime nextRun = calculateNextRunAt(job);
            job.setStatus(JobStatus.QUEUED); // back to the queue for a retry
            job.setNextRunAt(nextRun);
            jobRepository.save(job);

            log.info("Job [{}] scheduled for retry #{} at {}",
                    job.getId(), job.getRetryCount(), nextRun);
        }
    }

    private LocalDateTime calculateNextRunAt(Job job) {
        RetryPolicy policy = job.getQueue().getRetryPolicy();
        int baseDelay = (policy != null) ? policy.getBaseDelaySeconds() : 30;
        RetryType type = (policy != null) ? policy.getRetryType() : RetryType.EXPONENTIAL;

        long delaySeconds = RetryBackoff.delaySeconds(type, baseDelay, job.getRetryCount());

        log.info("Job [{}] retry delay: {} seconds (strategy: {})",
                job.getId(), delaySeconds, type);

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

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------
    private void freeWorker(Long workerId) {
        try {
            // BUSY -> IDLE only. If recovery already marked it DEAD, leave it alone.
            workerRepository.transitionStatus(workerId, WorkerStatus.BUSY, WorkerStatus.IDLE);
            publishWorker(workerId);
        } catch (Exception e) {
            log.error("Could not free worker [{}]: {}", workerId, e.getMessage());
        }
    }

    private void publishWorker(Long workerId) {
        workerRepository.findById(workerId).ifPresent(jobEventPublisher::publishWorkerUpdate);
    }

    private void publishJob(Long jobId) {
        jobRepository.findById(jobId).ifPresent(jobEventPublisher::publishJobUpdate);
    }

    private void logJobEvent(Job job, Worker worker, String message, String level) {
        JobLog jobLog = JobLog.builder()
                .job(job)
                .worker(worker)
                .message(message)
                .logLevel(level)
                .build();
        jobLogRepository.save(jobLog);
    }

    // -------------------------------------------------------------------
    // graceful shutdown
    // -------------------------------------------------------------------
    @PreDestroy
    public void onShutdown() {
        shuttingDown = true;
        log.info("Graceful shutdown initiated: no new jobs will be claimed");

        executor.shutdown(); // let running jobs finish
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Jobs still running after 30s; forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Graceful shutdown complete");
    }
}