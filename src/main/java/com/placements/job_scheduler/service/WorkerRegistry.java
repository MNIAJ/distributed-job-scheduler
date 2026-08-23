package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.WorkerRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerRegistry {

    private final WorkerRepository workerRepository;

    // How many worker threads we want running
    private static final int WORKER_COUNT = 3;

    @PostConstruct
    public void registerWorkers() {
        for (int i = 1; i <= WORKER_COUNT; i++) {
            String workerName = "worker-" + i;

            // If worker already exists (app restart), just update heartbeat
            // If not, create it fresh
            Worker worker = workerRepository.findByName(workerName)
                    .orElse(Worker.builder()
                            .name(workerName)
                            .status(WorkerStatus.IDLE)
                            .build());

            worker.setStatus(WorkerStatus.IDLE);
            worker.setLastHeartbeatAt(LocalDateTime.now());
            workerRepository.save(worker);

            log.info("Registered worker: {}", workerName);
        }
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }
}
