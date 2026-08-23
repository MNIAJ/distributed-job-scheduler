package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.RetryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetryPolicyRepository extends JpaRepository<RetryPolicy, Long> {
}