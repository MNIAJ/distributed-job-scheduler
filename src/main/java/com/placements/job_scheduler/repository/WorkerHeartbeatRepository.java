package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.WorkerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, Long> {
    List<WorkerHeartbeat> findByWorkerIdOrderByRecordedAtDesc(Long workerId);
    List<WorkerHeartbeat> findTop10ByWorkerIdOrderByRecordedAtDesc(Long workerId);
}