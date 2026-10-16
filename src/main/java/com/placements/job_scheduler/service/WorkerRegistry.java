package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.WorkerRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class WorkerRegistry {

    private final WorkerRepository workerRepository;
    private final JobRepository jobRepository;
    private final String instanceId;
    private final int workerCount;

    public WorkerRegistry(WorkerRepository workerRepository,
                          JobRepository jobRepository,
                          @Value("${worker.instance-id:}") String configuredInstanceId,
                          @Value("${server.port:8080}") String serverPort,
                          @Value("${worker.count:3}") int workerCount) {
        this.workerRepository = workerRepository;
        this.jobRepository = jobRepository;
        this.workerCount = workerCount;
        // Unique per running instance, stable across restarts of the same instance
        this.instanceId = configuredInstanceId.isBlank()
                ? resolveHostName() + "-" + serverPort
                : configuredInstanceId;
    }

    @PostConstruct
    public void registerWorkers() {
        for (int i = 1; i <= workerCount; i++) {
            String workerName = instanceId + "-worker-" + i;

            Worker worker = workerRepository.findByName(workerName)
                    .orElseGet(() -> Worker.builder().name(workerName).build());

            worker.setInstanceId(instanceId);
            worker.setStatus(WorkerStatus.IDLE);
            worker.setLastHeartbeatAt(LocalDateTime.now());
            worker = workerRepository.save(worker);

            // If this instance crashed earlier, its old workers may still own
            // RUNNING jobs. We just started, so nothing is really running: re-queue them.
            int recovered = jobRepository.requeueRunningJobsOfWorker(
                    worker.getId(), LocalDateTime.now(),
                    JobStatus.QUEUED, JobStatus.RUNNING);
            if (recovered > 0) {
                log.warn("Re-queued {} job(s) left RUNNING by previous run of {}",
                        recovered, workerName);
            }

            log.info("Registered worker: {}", workerName);
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }

    private static String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "local";
        }
    }
}