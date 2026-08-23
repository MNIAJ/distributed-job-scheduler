package com.placements.job_scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;


@Getter
@AllArgsConstructor
public class DashboardResponse {
    private long totalJobs;
    private long queuedJobs;
    private long runningJobs;
    private long completedJobs;
    private long failedJobs;
    private long deadJobs;
    private long totalWorkers;
    private long idleWorkers;
    private long busyWorkers;
    private long deadWorkers;
    private long dlqCount;
    private List<QueueStatsResponse> queueStats;
}
