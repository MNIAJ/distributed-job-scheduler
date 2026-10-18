package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateScheduledJobRequest;
import com.placements.job_scheduler.dto.response.ScheduledJobResponse;
import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.ScheduledJob;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronSchedulerService {

    private final ScheduledJobRepository scheduledJobRepository;
    private final JobRepository jobRepository;
    private final TenantAccessService tenantAccessService;

    // Check every minute if any cron job is due
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void spawnDueJobs() {
        // Locks the due rows until this transaction commits (SKIP LOCKED)
        List<ScheduledJob> dueJobs = scheduledJobRepository
                .findDueForUpdateSkipLocked();

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
        return CronExpression.parse(cronExpression)
                .next(ZonedDateTime.now())
                .toLocalDateTime();
    }

    @Transactional
    public ScheduledJobResponse create(CreateScheduledJobRequest request) {
        // 404 if the queue does not exist, 403 if it belongs to someone else
        Queue queue = tenantAccessService.requireOwnedQueue(request.getQueueId());

        LocalDateTime firstRun = calculateNextRun(request.getCronExpression());

        ScheduledJob scheduledJob = ScheduledJob.builder()
                .queue(queue)
                .jobType(request.getJobType())
                .payload(request.getPayload())
                .cronExpression(request.getCronExpression())
                .priority(request.getPriority())
                .nextRunAt(firstRun)
                .isActive(true)
                .build();

        return toResponse(scheduledJobRepository.save(scheduledJob));
    }

    // Only this user's schedules
    @Transactional(readOnly = true)
    public List<ScheduledJobResponse> getAllForCurrentUser() {
        return scheduledJobRepository.findAllForUser(tenantAccessService.currentUserId())
                .stream().map(this::toResponse).toList();
    }

    private ScheduledJobResponse toResponse(ScheduledJob s) {
        return new ScheduledJobResponse(s.getId(), s.getQueue().getId(), s.getJobType(),
                s.getPayload(), s.getCronExpression(), s.getPriority(), s.getIsActive(),
                s.getNextRunAt(), s.getLastRunAt(), s.getCreatedAt());
    }

    @Transactional
    public void deactivate(Long id) {
        ScheduledJob scheduledJob = tenantAccessService.requireOwnedScheduledJob(id);
        scheduledJob.setIsActive(false);
        scheduledJobRepository.save(scheduledJob);
    }
}