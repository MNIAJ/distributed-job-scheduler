package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.DeadLetterQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueue, Long> {
    List<DeadLetterQueue> findByQueueId(Long queueId);
}
