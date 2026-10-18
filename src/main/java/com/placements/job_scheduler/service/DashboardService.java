package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.response.DashboardResponse;
import com.placements.job_scheduler.dto.response.QueueStatsResponse;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.DeadLetterQueueRepository;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final QueueRepository queueRepository;
    private final DeadLetterQueueRepository dlqRepository;
    private final TenantAccessService tenantAccessService;

    public DashboardResponse getStats() {
        Long userId = tenantAccessService.currentUserId();

        // Job counts: only the current user's jobs
        long total = jobRepository.countAllForUser(userId);
        long queued = jobRepository.countByStatusForUser(JobStatus.QUEUED, userId);
        long running = jobRepository.countByStatusForUser(JobStatus.RUNNING, userId);
        long completed = jobRepository.countByStatusForUser(JobStatus.COMPLETED, userId);
        long failed = jobRepository.countByStatusForUser(JobStatus.FAILED, userId);
        long dead = jobRepository.countByStatusForUser(JobStatus.DEAD, userId);

        // Workers are shared infrastructure, so these stay system-wide
        long totalWorkers = workerRepository.count();
        long idle = workerRepository.countByStatus(WorkerStatus.IDLE);
        long busy = workerRepository.countByStatus(WorkerStatus.BUSY);
        long deadWorkers = workerRepository.countByStatus(WorkerStatus.DEAD);

        long dlqCount = dlqRepository.countForUser(userId);

        // Per-queue breakdown: only this user's queues
        List<QueueStatsResponse> queueStats = queueRepository.findByProjectUserId(userId)
                .stream()
                .map(q -> new QueueStatsResponse(
                        q.getId(),
                        q.getName(),
                        jobRepository.countByQueueIdAndStatus(q.getId(), JobStatus.QUEUED),
                        jobRepository.countByQueueIdAndStatus(q.getId(), JobStatus.RUNNING),
                        jobRepository.countByQueueIdAndStatus(q.getId(), JobStatus.COMPLETED),
                        jobRepository.countByQueueIdAndStatus(q.getId(), JobStatus.FAILED),
                        q.getStatus()
                ))
                .collect(Collectors.toList());

        return new DashboardResponse(total, queued, running, completed,
                failed, dead, totalWorkers, idle, busy, deadWorkers,
                dlqCount, queueStats);
    }
}
