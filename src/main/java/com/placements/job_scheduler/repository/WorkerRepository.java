package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByName(String name);

    // Find workers whose heartbeat is older than a given time (for stuck job detection)
    List<Worker> findByLastHeartbeatAtBeforeAndStatus(LocalDateTime time, WorkerStatus status);
    List<Worker> findByStatus(WorkerStatus status);
    List<Worker> findByStatusNot(WorkerStatus status);

    long countByStatus(WorkerStatus status);

    // ---- new in Change 3 ----
    List<Worker> findByInstanceIdAndStatus(String instanceId, WorkerStatus status);

    // Any worker (not already DEAD) whose heartbeat is older than 'time'
    List<Worker> findByLastHeartbeatAtBeforeAndStatusNot(LocalDateTime time, WorkerStatus status);

    // Refresh heartbeat for one instance's workers with a single UPDATE,
    // so we never overwrite a worker's status with stale entity data.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Worker w set w.lastHeartbeatAt = :now " +
            "where w.instanceId = :instanceId and w.status <> :dead")
    int touchHeartbeats(@Param("instanceId") String instanceId,
                        @Param("now") LocalDateTime now,
                        @Param("dead") WorkerStatus dead);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Worker w set w.status = :status where w.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") WorkerStatus status);

    List<Worker> findByInstanceIdAndStatusNot(String instanceId, WorkerStatus status);

    // Guarded status change: only moves from 'from' to 'to'. Returns rows changed (0 or 1).
    // flushAutomatically makes Hibernate write pending changes first, so nothing is lost
    // when clearAutomatically empties the persistence context.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update Worker w set w.status = :to where w.id = :id and w.status = :from")
    int transitionStatus(@Param("id") Long id,
                         @Param("from") WorkerStatus from,
                         @Param("to") WorkerStatus to);


}