package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByQueueId(Long queueId);
    List<Job> findByStatus(JobStatus status);

    // Count running jobs for a queue (for concurrency limit check)
    long countByQueueIdAndStatus(Long queueId, JobStatus status);

    // The most important query — we'll add the atomic claiming one here in Phase 5
}
