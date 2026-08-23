package com.placements.job_scheduler.dto.response;

import com.placements.job_scheduler.enums.QueueStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueStatsResponse {
    private Long queueId;
    private String queueName;
    private long queued;
    private long running;
    private long completed;
    private long failed;
    private QueueStatus status;
}
