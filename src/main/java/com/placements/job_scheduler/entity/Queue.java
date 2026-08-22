package com.placements.job_scheduler.entity;

import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.enums.RetryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "queues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Queue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    // Higher number = higher priority (1-10)
    @Column(nullable = false)
    private Integer priority = 5;

    // Max jobs running at the same time from this queue
    @Column(name = "concurrency_limit", nullable = false)
    private Integer concurrencyLimit = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status = QueueStatus.ACTIVE;

    // Retry config stored directly on queue for simplicity
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "retry_type", nullable = false)
    private RetryType retryType = RetryType.EXPONENTIAL;

    @Column(name = "base_delay_seconds", nullable = false)
    private Integer baseDelaySeconds = 30;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
