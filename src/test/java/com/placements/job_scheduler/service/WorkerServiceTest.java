package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.*;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.enums.RetryType;
import com.placements.job_scheduler.enums.WorkerStatus;
import com.placements.job_scheduler.repository.*;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WorkerServiceTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private RetryPolicyRepository retryPolicyRepository;

    private Queue testQueue;
    private Worker testWorker;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .name("Test")
                .email("worker" + System.currentTimeMillis() + "@test.com")
                .password("pass")
                .build());

        Project project = projectRepository.save(Project.builder()
                .user(user).name("Test Project").build());

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

        testWorker = workerRepository.save(Worker.builder()
                .name("test-worker-" + System.currentTimeMillis())
                .status(WorkerStatus.IDLE)
                .lastHeartbeatAt(LocalDateTime.now())
                .build());
    }

    @Test
    void shouldClaimJobAtomically() {
        // Create a job
        Job job = jobRepository.save(Job.builder()
                .queue(testQueue)
                .jobType("TEST")
                .payload("{}")
                .status(JobStatus.QUEUED)
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .nextRunAt(LocalDateTime.now().minusSeconds(1))
                .build());

        // Claim it
        Optional<Job> claimed = jobRepository.claimNextJob();

        assertTrue(claimed.isPresent());
        assertEquals(job.getId(), claimed.get().getId());
    }

    @Test
    void shouldNotClaimJobFromPausedQueue() {
        testQueue.setStatus(QueueStatus.PAUSED);
        queueRepository.save(testQueue);

        jobRepository.save(Job.builder()
                .queue(testQueue)
                .jobType("TEST")
                .payload("{}")
                .status(JobStatus.QUEUED)
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .nextRunAt(LocalDateTime.now().minusSeconds(1))
                .build());

        Optional<Job> claimed = jobRepository.claimNextJob();
        assertTrue(claimed.isEmpty());
    }

    @Test
    void shouldNotClaimJobScheduledInFuture() {
        jobRepository.save(Job.builder()
                .queue(testQueue)
                .jobType("TEST")
                .payload("{}")
                .status(JobStatus.QUEUED)
                .priority(5)
                .retryCount(0)
                .maxRetries(3)
                .nextRunAt(LocalDateTime.now().plusHours(1)) // future
                .build());

        Optional<Job> claimed = jobRepository.claimNextJob();
        assertTrue(claimed.isEmpty());
    }
}