package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByName(String name);
    // Find workers whose heartbeat is older than a given time (for stuck job detection)
    List<Worker> findByLastHeartbeatAtBeforeAndStatus(LocalDateTime time, WorkerStatus status);
}
