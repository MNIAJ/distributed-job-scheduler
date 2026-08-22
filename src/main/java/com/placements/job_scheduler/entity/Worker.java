package com.placements.job_scheduler.entity;

import com.placements.job_scheduler.enums.WorkerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. "worker-1", "worker-2"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerStatus status = WorkerStatus.IDLE;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastHeartbeatAt = LocalDateTime.now();
    }
}