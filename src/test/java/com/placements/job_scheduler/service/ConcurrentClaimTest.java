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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the claim query hands every job to exactly one worker, even when many
 * workers race. Not transactional on purpose: each claim is a real, committed
 * transaction, like in production.
 *
 * IMPORTANT: stop the application before running this test, otherwise the
 * running app's own workers will claim and execute these test jobs.
 */
@SpringBootTest(properties = {"worker.instance-id=test-instance", "worker.count=1"})
@ActiveProfiles("test")
class ConcurrentClaimTest {

    private static final int JOB_COUNT = 40;
    private static final int THREAD_COUNT = 8;
    private static final int TOP = 1_000_000;

    @Autowired private JobRepository jobRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private User user;
    private Project project;
    private Queue queue;
    private Set<Long> ourJobIds;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .name("Claim Test")
                .email("claim" + System.nanoTime() + "@test.com")
                .password("pass")
                .build());
        project = projectRepository.save(Project.builder()
                .user(user).name("Claim Project").build());
        // very high priority so our jobs always sort first; big limit so it never blocks
        queue = queueRepository.save(Queue.builder()
                .project(project)
                .name("claim-queue")
                .priority(TOP)
                .concurrencyLimit(1000)
                .status(QueueStatus.ACTIVE)
                .build());

        ourJobIds = new HashSet<>();
        for (int i = 0; i < JOB_COUNT; i++) {
            Job job = jobRepository.save(Job.builder()
                    .queue(queue)
                    .jobType("CLAIM_TEST")
                    .payload("{}")
                    .status(JobStatus.QUEUED)
                    .priority(TOP)
                    .retryCount(0)
                    .maxRetries(3)
                    .nextRunAt(LocalDateTime.now().minusMinutes(1))
                    .build());
            ourJobIds.add(job.getId());
        }
    }

    @AfterEach
    void cleanUp() {
        if (queue != null) {
            jobRepository.deleteAll(jobRepository.findByQueueId(queue.getId()));
            queueRepository.deleteById(queue.getId());
        }
        if (project != null) {
            projectRepository.deleteById(project.getId());
        }
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void everyJobIsClaimedByExactlyOneWorker() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(pool.submit(() -> {
                List<Long> mine = new ArrayList<>();
                startGate.await(); // all threads start at the same moment
                while (true) {
                    Long claimedId = tx.execute(status -> claimOne(status));
                    if (claimedId == null) break;
                    mine.add(claimedId);
                }
                return mine;
            }));
        }
        startGate.countDown();

        List<Long> allClaimed = new ArrayList<>();
        for (Future<List<Long>> future : futures) {
            allClaimed.addAll(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertEquals(JOB_COUNT, allClaimed.size(), "every job should be claimed");
        assertEquals(JOB_COUNT, new HashSet<>(allClaimed).size(),
                "a job was claimed by more than one worker!");
        assertEquals(ourJobIds, new HashSet<>(allClaimed));
    }

    // One claim = one short transaction, exactly like WorkerService.claimJob
    private Long claimOne(TransactionStatus status) {
        Optional<Job> claimed = jobRepository.claimNextJob();
        if (claimed.isEmpty()) {
            return null;
        }
        Job job = claimed.get();
        if (!ourJobIds.contains(job.getId())) {
            // someone else's job (other data in this database): leave it untouched
            status.setRollbackOnly();
            return null;
        }
        job.setStatus(JobStatus.RUNNING);
        jobRepository.save(job);
        return job.getId();
    }
}