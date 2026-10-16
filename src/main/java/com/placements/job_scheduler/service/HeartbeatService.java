package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.entity.WorkerHeartbeat;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerHeartbeatRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private static final int STALE_AFTER_MINUTES = 2;

    private final WorkerRepository workerRepository;
    private final JobRepository jobRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final WorkerRegistry workerRegistry;
    private final JobEventPublisher jobEventPublisher;

    // Each instance reports ONLY for its own workers ("I am alive")
    @Scheduled(fixedDelay = 30000)
    public void updateHeartbeats() {
        String instanceId = workerRegistry.getInstanceId();
        LocalDateTime now = LocalDateTime.now();

        // One UPDATE statement: never overwrites a worker's status
        workerRepository.touchHeartbeats(instanceId, now, WorkerStatus.DEAD);

        // History rows (for the dashboard)
        List<Worker> myWorkers = workerRepository
                .findByInstanceIdAndStatusNot(instanceId, WorkerStatus.DEAD);
        for (Worker worker : myWorkers) {
            heartbeatRepository.save(WorkerHeartbeat.builder()
                    .worker(worker)
                    .status(worker.getStatus())
                    .recordedAt(now)
                    .build());
        }
    }

    // ANY instance can run this: it looks at ALL workers, so if one instance
    // dies, a surviving instance recovers its jobs.
    @Scheduled(fixedDelay = 60000)
    public void detectDeadWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES);

        List<Worker> staleWorkers = workerRepository
                .findByLastHeartbeatAtBeforeAndStatusNot(threshold, WorkerStatus.DEAD);

        for (Worker worker : staleWorkers) {
            log.warn("Dead worker detected: [{}]. Recovering its jobs.", worker.getName());

            // One atomic UPDATE: RUNNING jobs of this worker go back to QUEUED
            int requeued = jobRepository.requeueRunningJobsOfWorker(
                    worker.getId(), LocalDateTime.now(),
                    JobStatus.QUEUED, JobStatus.RUNNING);

            workerRepository.updateStatus(worker.getId(), WorkerStatus.DEAD);

            if (requeued > 0) {
                log.info("Recovered {} job(s) from dead worker [{}]",
                        requeued, worker.getName());
            }

            worker.setStatus(WorkerStatus.DEAD);
            jobEventPublisher.publishWorkerUpdate(worker);
        }
    }
}