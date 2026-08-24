package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateJobRequest;
import com.placements.job_scheduler.dto.response.JobResponse;
import com.placements.job_scheduler.entity.Project;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.RetryPolicy;
import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.enums.RetryType;
import com.placements.job_scheduler.repository.*;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class JobServiceTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobService jobService;

    private Queue testQueue;

    @Autowired
    private RetryPolicyRepository retryPolicyRepository;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .name("Test User")
                .email("test" + System.currentTimeMillis() + "@test.com")
                .password("password")
                .build());

        Project project = projectRepository.save(Project.builder()
                .user(user)
                .name("Test Project")
                .build());

        RetryPolicy retryPolicy = retryPolicyRepository.save(RetryPolicy.builder()
                .name("test-policy")
                .maxRetries(3)
                .retryType(RetryType.EXPONENTIAL)
                .baseDelaySeconds(30)
                .build());

        testQueue = queueRepository.save(Queue.builder()
                .project(project)
                .name("test-queue")
                .priority(5)
                .concurrencyLimit(3)
                .retryPolicy(retryPolicy)
                .status(QueueStatus.ACTIVE)
                .build());
    }

    @Test
    void shouldCreateJobWithQueuedStatus() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(testQueue.getId());
        request.setJobType("EMAIL");
        request.setPayload("{\"to\": \"test@test.com\"}");
        request.setPriority(5);

        JobResponse response = jobService.create(request);

        assertNotNull(response.getId());
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals("EMAIL", response.getJobType());
        assertEquals(0, response.getRetryCount());
    }

    @Test
    void shouldEnforceIdempotencyKey() {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(testQueue.getId());
        request.setJobType("EMAIL");
        request.setPayload("{\"to\": \"test@test.com\"}");
        request.setPriority(5);
        request.setIdempotencyKey("unique-key-123");

        JobResponse first = jobService.create(request);
        JobResponse second = jobService.create(request);

        // Same idempotency key should return the same job
        assertEquals(first.getId(), second.getId());

        // Only one job should exist in DB
        long count = jobRepository.findByQueueId(testQueue.getId()).size();
        assertEquals(1, count);
    }

    @Test
    void shouldRejectJobCreationOnPausedQueue() {
        testQueue.setStatus(QueueStatus.PAUSED);
        queueRepository.save(testQueue);

        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(testQueue.getId());
        request.setJobType("EMAIL");
        request.setPayload("{}");
        request.setPriority(5);

        assertThrows(RuntimeException.class, () -> jobService.create(request));
    }
}
