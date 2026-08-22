package com.placements.job_scheduler.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_job_id")
    private Job originalJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id")
    private Queue queue;

    @Column(name = "job_type")
    private String jobType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "total_attempts")
    private Integer totalAttempts;

    @Column(name = "moved_at")
    private LocalDateTime movedAt;

    @PrePersist
    protected void onCreate() {
        movedAt = LocalDateTime.now();
    }
}
