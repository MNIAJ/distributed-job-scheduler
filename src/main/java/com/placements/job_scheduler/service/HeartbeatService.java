package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
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
public class HeartbeatService {

    private final WorkerRepository workerRepository;
    private final JobRepository jobRepository;

    // Update heartbeat every 30 seconds
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void updateHeartbeats() {
        List<Worker> activeWorkers = workerRepository
                .findByStatusNot(WorkerStatus.DEAD);

        activeWorkers.forEach(worker -> {
            worker.setLastHeartbeatAt(LocalDateTime.now());
            workerRepository.save(worker);
        });
    }

    // Check for dead workers every 60 seconds
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void detectStuckJobs() {
        // A worker is considered dead if no heartbeat for 2 minutes
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(2);

        List<Worker> deadWorkers = workerRepository
                .findByLastHeartbeatAtBeforeAndStatus(
                        staleThreshold, WorkerStatus.BUSY);

        for (Worker deadWorker : deadWorkers) {
            log.warn("Dead worker detected: [{}]. Recovering stuck jobs.",
                    deadWorker.getName());

            // Find all jobs this dead worker was running
            List<Job> stuckJobs = jobRepository
                    .findByClaimedByAndStatus(deadWorker, JobStatus.RUNNING);

            for (Job job : stuckJobs) {
                // Reset to QUEUED so another worker picks it up
                job.setStatus(JobStatus.QUEUED);
                job.setClaimedBy(null);
                job.setClaimedAt(null);
                job.setNextRunAt(LocalDateTime.now());
                jobRepository.save(job);
                log.info("Recovered stuck job [{}] from dead worker [{}]",
                        job.getId(), deadWorker.getName());
            }

            // Mark worker as dead
            deadWorker.setStatus(WorkerStatus.DEAD);
            workerRepository.save(deadWorker);
        }
    }
}
