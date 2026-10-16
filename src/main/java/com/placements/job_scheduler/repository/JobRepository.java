package com.placements.job_scheduler.repository;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Worker;
import com.placements.job_scheduler.enums.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    // Atomic claiming
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

    // ---- new in Change 3 ----

    // Load a job and take a row lock, so completion and recovery
    // can never modify the same job at the same time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

    // Put every RUNNING job of a dead worker back in the queue (one atomic UPDATE)
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Job j set j.status = :queued, j.claimedBy = null, " +
            "j.claimedAt = null, j.nextRunAt = :now " +
            "where j.claimedBy.id = :workerId and j.status = :running")
    int requeueRunningJobsOfWorker(@Param("workerId") Long workerId,
                                   @Param("now") LocalDateTime now,
                                   @Param("queued") JobStatus queued,
                                   @Param("running") JobStatus running);


    // Jobs of one status, only for queues owned by this user
    @Query("select j from Job j where j.status = :status " +
            "and j.queue.project.user.id = :userId")
    Page<Job> findByStatusForUser(@Param("status") JobStatus status,
                                  @Param("userId") Long userId,
                                  Pageable pageable);

    // Jobs a worker is running, only those owned by this user
    @Query("select j from Job j where j.claimedBy.id = :workerId " +
            "and j.status = :status and j.queue.project.user.id = :userId")
    List<Job> findByWorkerForUser(@Param("workerId") Long workerId,
                                  @Param("status") JobStatus status,
                                  @Param("userId") Long userId);
}