package com.placements.job_scheduler.entity;

import com.placements.job_scheduler.enums.RetryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "retry_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

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