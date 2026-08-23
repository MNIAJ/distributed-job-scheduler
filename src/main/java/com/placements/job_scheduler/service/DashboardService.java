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

    public DashboardResponse getStats() {
        // Job counts by status
        long total = jobRepository.count();
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long dead = jobRepository.countByStatus(JobStatus.DEAD);

        // Worker counts
        long totalWorkers = workerRepository.count();
        long idle = workerRepository.countByStatus(WorkerStatus.IDLE);
        long busy = workerRepository.countByStatus(WorkerStatus.BUSY);
        long deadWorkers = workerRepository.countByStatus(WorkerStatus.DEAD);

        // DLQ count
        long dlqCount = dlqRepository.count();

        // Per-queue breakdown
        List<QueueStatsResponse> queueStats = queueRepository.findAll()
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
