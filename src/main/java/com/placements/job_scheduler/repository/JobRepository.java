package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByQueueId(Long queueId);
    List<Job> findByStatus(JobStatus status);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);
    Page<Job> findByQueueId(Long queueId, Pageable pageable);
    Page<Job> findByQueueIdAndStatus(Long queueId,
                                     JobStatus status,
                                     Pageable pageable);

    // Count running jobs for a queue (for concurrency limit check)
    long countByQueueIdAndStatus(Long queueId, JobStatus status);

    // Atomic claiming
    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
    SELECT j.* FROM jobs j
    JOIN queues q ON j.queue_id = q.id
    WHERE j.status = 'QUEUED'
    AND q.status = 'ACTIVE'
    AND j.next_run_at <= NOW()
    AND (
        SELECT COUNT(*) FROM jobs running
        WHERE running.queue_id = j.queue_id
        AND running.status = 'RUNNING'
    ) < q.concurrency_limit
    ORDER BY q.priority DESC, j.priority DESC, j.created_at ASC
    FOR UPDATE OF j SKIP LOCKED
    LIMIT 1
    """, nativeQuery = true)

    Optional<Job> claimNextJob();

    List<Job> findByClaimedByAndStatus(Worker worker, JobStatus status);

    long countByStatus(JobStatus status);

}
