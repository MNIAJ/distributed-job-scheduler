package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    List<JobExecution> findByJobId(Long jobId);
}
