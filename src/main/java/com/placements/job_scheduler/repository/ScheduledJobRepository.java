package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
    List<ScheduledJob> findByIsActiveTrueAndNextRunAtBefore(LocalDateTime now);
    List<ScheduledJob> findByQueueId(Long queueId);

    // Due schedules, locked so that only ONE instance spawns each of them.
    @Query(value = """
        SELECT * FROM scheduled_jobs
        WHERE is_active = true
          AND next_run_at <= NOW()
        ORDER BY next_run_at
        FOR UPDATE SKIP LOCKED
        LIMIT 50
        """, nativeQuery = true)
    List<ScheduledJob> findDueForUpdateSkipLocked();

    // Only the schedules whose queue belongs to this user
    @Query("select s from ScheduledJob s " +
            "where s.queue.project.user.id = :userId order by s.id")
    List<ScheduledJob> findAllForUser(@Param("userId") Long userId);
}