package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateScheduledJobRequest;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.ScheduledJob;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import com.placements.job_scheduler.repository.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronSchedulerService {

    private final ScheduledJobRepository scheduledJobRepository;
    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;

    // Check every minute if any cron job is due
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void spawnDueJobs() {
        List<ScheduledJob> dueJobs = scheduledJobRepository
                .findByIsActiveTrueAndNextRunAtBefore(LocalDateTime.now());

        for (ScheduledJob scheduledJob : dueJobs) {
            // Create a real job from the template
            Job job = Job.builder()
                    .queue(scheduledJob.getQueue())
                    .jobType(scheduledJob.getJobType())
                    .payload(scheduledJob.getPayload())
                    .priority(scheduledJob.getPriority())
                    .status(JobStatus.QUEUED)
                    .retryCount(0)
                    .maxRetries(scheduledJob.getQueue().getRetryPolicy() != null
                            ? scheduledJob.getQueue().getRetryPolicy().getMaxRetries()
                            : 3)
                    .nextRunAt(LocalDateTime.now())
                    .build();
            jobRepository.save(job);

            // Calculate next run time
            scheduledJob.setLastRunAt(LocalDateTime.now());
            scheduledJob.setNextRunAt(
                    calculateNextRun(scheduledJob.getCronExpression()));
            scheduledJobRepository.save(scheduledJob);

            log.info("Spawned job for cron schedule [{}]: {}",
                    scheduledJob.getCronExpression(),
                    scheduledJob.getJobType());
        }
    }

    private LocalDateTime calculateNextRun(String cronExpression) {
        // Simple implementation: for common patterns
        // In production, use CronExpression from Spring
        return org.springframework.scheduling.support
                .CronExpression.parse(cronExpression)
                .next(java.time.ZonedDateTime.now())
                .toLocalDateTime();
    }

    public ScheduledJob create(CreateScheduledJobRequest request) {
        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Queue not found"));

        LocalDateTime firstRun = org.springframework.scheduling.support
                .CronExpression.parse(request.getCronExpression())
                .next(java.time.ZonedDateTime.now())
                .toLocalDateTime();

        ScheduledJob scheduledJob = ScheduledJob.builder()
                .queue(queue)
                .jobType(request.getJobType())
                .payload(request.getPayload())
                .cronExpression(request.getCronExpression())
                .priority(request.getPriority())
                .nextRunAt(firstRun)
                .isActive(true)
                .build();

        return scheduledJobRepository.save(scheduledJob);
    }
}