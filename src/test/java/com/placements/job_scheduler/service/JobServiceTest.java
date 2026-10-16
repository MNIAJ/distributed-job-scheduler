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
import com.placements.job_scheduler.exception.ForbiddenException;
import com.placements.job_scheduler.repository.JobRepository;
import com.placements.job_scheduler.repository.ProjectRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import com.placements.job_scheduler.repository.RetryPolicyRepository;
import com.placements.job_scheduler.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"worker.instance-id=test-instance", "worker.count=1"})
@ActiveProfiles("test")
@Transactional
class JobServiceTest {

    @Autowired private JobRepository jobRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RetryPolicyRepository retryPolicyRepository;
    @Autowired private JobService jobService;

    private User userA;
    private User userB;
    private Queue queueA;
    private Queue queueB;

    @BeforeEach
    void setUp() {
        RetryPolicy policy = retryPolicyRepository.save(RetryPolicy.builder()
                .name("test-policy")
                .maxRetries(3)
                .retryType(RetryType.EXPONENTIAL)
                .baseDelaySeconds(30)
                .build());

        userA = saveUser("a");
        userB = saveUser("b");
        queueA = saveQueue(userA, policy);
        queueB = saveQueue(userB, policy);

        authenticateAs(userA);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private User saveUser(String tag) {
        return userRepository.save(User.builder()
                .name("User " + tag)
                .email(tag + System.nanoTime() + "@test.com")
                .password("pass")
                .build());
    }

    private Queue saveQueue(User owner, RetryPolicy policy) {
        Project project = projectRepository.save(Project.builder()
                .user(owner).name("Project of " + owner.getName()).build());
        return queueRepository.save(Queue.builder()
                .project(project)
                .name("queue-" + owner.getName())
                .priority(5)
                .concurrencyLimit(3)
                .retryPolicy(policy)
                .status(QueueStatus.ACTIVE)
                .build());
    }

    // Pretend this user is logged in (CurrentUserProvider reads the email from here)
    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
    }

    private CreateJobRequest request(Queue queue, String idempotencyKey) {
        CreateJobRequest request = new CreateJobRequest();
        request.setQueueId(queue.getId());
        request.setJobType("EMAIL");
        request.setPayload("{\"to\": \"test@test.com\"}");
        request.setPriority(5);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    @Test
    void shouldCreateJobWithQueuedStatus() {
        JobResponse response = jobService.create(request(queueA, null));

        assertNotNull(response.getId());
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals("EMAIL", response.getJobType());
        assertEquals(0, response.getRetryCount());
    }

    @Test
    void shouldEnforceIdempotencyKey() {
        JobResponse first = jobService.create(request(queueA, "unique-key-123"));
        JobResponse second = jobService.create(request(queueA, "unique-key-123"));

        // Same idempotency key returns the same job
        assertEquals(first.getId(), second.getId());
        assertEquals(1, jobRepository.findByQueueId(queueA.getId()).size());
    }

    @Test
    void shouldRejectJobCreationOnPausedQueue() {
        queueA.setStatus(QueueStatus.PAUSED);
        queueRepository.saveAndFlush(queueA);

        assertThrows(RuntimeException.class,
                () -> jobService.create(request(queueA, null)));
    }

    @Test
    void shouldRejectJobCreationInAnotherUsersQueue() {
        authenticateAs(userB);

        assertThrows(ForbiddenException.class,
                () -> jobService.create(request(queueA, null)));
    }

    @Test
    void shouldHideJobsFromOtherUsers() {
        Long jobId = jobService.create(request(queueA, null)).getId();

        // owner sees it
        assertTrue(jobService.getByStatusPaged(JobStatus.QUEUED, PageRequest.of(0, 50))
                .getContent().stream().anyMatch(j -> j.getId().equals(jobId)));

        // another user cannot read it, cancel it, or find it in lists
        authenticateAs(userB);
        assertThrows(ForbiddenException.class, () -> jobService.getById(jobId));
        assertThrows(ForbiddenException.class, () -> jobService.cancel(jobId));
        assertFalse(jobService.getByStatusPaged(JobStatus.QUEUED, PageRequest.of(0, 50))
                .getContent().stream().anyMatch(j -> j.getId().equals(jobId)));
    }

    @Test
    void shouldNotLeakJobsThroughReusedIdempotencyKey() {
        jobService.create(request(queueA, "shared-key"));

        authenticateAs(userB);

        // B reuses A's key in B's own queue: must not return A's job
        assertThrows(ForbiddenException.class,
                () -> jobService.create(request(queueB, "shared-key")));
    }
}