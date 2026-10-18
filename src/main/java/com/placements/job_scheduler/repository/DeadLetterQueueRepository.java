package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.DeadLetterQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueue, Long> {
    List<DeadLetterQueue> findByQueueId(Long queueId);

    @Query("select count(d) from DeadLetterQueue d " +
            "where d.queue.project.user.id = :userId")
    long countForUser(@Param("userId") Long userId);

    @Query("select d from DeadLetterQueue d " +
            "where d.queue.project.user.id = :userId order by d.movedAt desc")
    List<DeadLetterQueue> findAllForUser(@Param("userId") Long userId);
}