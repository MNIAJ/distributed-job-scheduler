package com.placements.job_scheduler.entity;

import com.placements.job_scheduler.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by")
    private Worker claimedBy; // null until a worker picks it up

    @Column(name = "job_type", nullable = false)
    private String jobType; // e.g. "EMAIL", "REPORT", "EXPORT"

    @Column(columnDefinition = "TEXT")
    private String payload; // JSON string — the job's input data

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    // 1 = lowest, 10 = highest
    @Column(nullable = false)
    private Integer priority = 5;

    // How many times has this job been attempted
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    // Copied from Queue when job is created
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    // When should this job run? null = run immediately
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // When is the next retry attempt due?
    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    // Prevents duplicate job creation
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (nextRunAt == null) {
            nextRunAt = scheduledAt != null ? scheduledAt : LocalDateTime.now();
        }
    }
}