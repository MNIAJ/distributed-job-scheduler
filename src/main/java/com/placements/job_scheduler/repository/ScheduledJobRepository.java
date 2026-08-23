package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
    List<ScheduledJob> findByIsActiveTrueAndNextRunAtBefore(LocalDateTime now);
    List<ScheduledJob> findByQueueId(Long queueId);
}