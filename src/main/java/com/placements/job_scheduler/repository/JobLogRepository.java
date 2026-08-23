package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    List<JobLog> findByJobIdOrderByCreatedAtAsc(Long jobId);
}
