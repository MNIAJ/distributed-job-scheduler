package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueueRepository extends JpaRepository<Queue, Long> {
    List<Queue> findByProjectId(Long projectId);
}
