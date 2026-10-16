package com.placements.job_scheduler.service;

import com.placements.job_scheduler.entity.Job;
import com.placements.job_scheduler.entity.Project;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.enums.JobStatus;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.ProjectRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import com.placements.job_scheduler.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the claim query (FOR UPDATE SKIP LOCKED) against the real database.
 * Test data uses a very high priority so it always sorts first, and every
 * test rolls back, so other data in the database is never touched.
 */
@SpringBootTest(properties = {"worker.instance-id=test-instance", "worker.count=1"})
@ActiveProfiles("test")
@Transactional
class WorkerServiceTest {

    private static final int TOP = 1_000_000;

    @Autowired private JobRepository jobRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    private Queue queue;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .name("Worker Test")
                .email("worker" + System.nanoTime() + "@test.com")
                .password("pass")
                .build());

        Project project = projectRepository.save(Project.builder()
                .user(user).name("Test Project").build());

        queue = queueRepository.save(Queue.builder()
                .project(project)
                .name("test-queue")
                .priority(TOP)
                .concurrencyLimit(3)
                .status(QueueStatus.ACTIVE)
                .build());
    }

    private Job newJob(int priority, JobStatus status, LocalDateTime nextRunAt) {
        return jobRepository.save(Job.builder()
                .queue(queue)
                .jobType("TEST")
                .payload("{}")
                .status(status)
                .priority(priority)
                .retryCount(0)
                .maxRetries(3)
                .nextRunAt(nextRunAt)
                .build());
    }

    @Test
    void shouldClaimHighestPriorityJobFirst() {
        newJob(10, JobStatus.QUEUED, LocalDateTime.now().minusMinutes(1));
        Job high = newJob(TOP, JobStatus.QUEUED, LocalDateTime.now().minusMinutes(1));

        Optional<Job> claimed = jobRepository.claimNextJob();

        assertTrue(claimed.isPresent());
        assertEquals(high.getId(), claimed.get().getId());
    }

    @Test
    void shouldNotClaimJobFromPausedQueue() {
        queue.setStatus(QueueStatus.PAUSED);
        queueRepository.saveAndFlush(queue);
        newJob(TOP, JobStatus.QUEUED, LocalDateTime.now().minusMinutes(1));

        Optional<Job> claimed = jobRepository.claimNextJob();

        // other data in the database may be claimable, but never ours
        claimed.ifPresent(job ->
                assertNotEquals(queue.getId(), job.getQueue().getId()));
    }

    @Test
    void shouldNotClaimJobScheduledInFuture() {
        Job future = newJob(TOP, JobStatus.QUEUED, LocalDateTime.now().plusHours(1));

        Optional<Job> claimed = jobRepository.claimNextJob();

        claimed.ifPresent(job -> assertNotEquals(future.getId(), job.getId()));
    }

    @Test
    void shouldRespectQueueConcurrencyLimit() {
        queue.setConcurrencyLimit(1);
        queueRepository.saveAndFlush(queue);

        // the queue's single slot is already taken by a running job
        newJob(TOP, JobStatus.RUNNING, LocalDateTime.now().minusMinutes(1));
        Job waiting = newJob(TOP, JobStatus.QUEUED, LocalDateTime.now().minusMinutes(1));

        Optional<Job> claimed = jobRepository.claimNextJob();

        claimed.ifPresent(job -> assertNotEquals(waiting.getId(), job.getId()));
    }
}