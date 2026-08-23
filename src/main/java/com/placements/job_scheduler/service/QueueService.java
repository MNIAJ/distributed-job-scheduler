package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateQueueRequest;
import com.placements.job_scheduler.dto.response.QueueResponse;
import com.placements.job_scheduler.entity.Project;
import com.placements.job_scheduler.entity.Queue;
import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.enums.QueueStatus;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.ProjectRepository;
import com.placements.job_scheduler.repository.QueueRepository;
import com.placements.job_scheduler.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserProvider currentUserProvider;

    public QueueResponse create(Long projectId, CreateQueueRequest request) {
        Project project = getValidatedProject(projectId);

        Queue queue = Queue.builder()
                .project(project)
                .name(request.getName())
                .priority(request.getPriority())
                .concurrencyLimit(request.getConcurrencyLimit())
                .maxRetries(request.getMaxRetries())
                .retryType(request.getRetryType())
                .baseDelaySeconds(request.getBaseDelaySeconds())
                .status(QueueStatus.ACTIVE)
                .build();

        return toResponse(queueRepository.save(queue));
    }

    public List<QueueResponse> getAll(Long projectId) {
        getValidatedProject(projectId); // validates ownership
        return queueRepository.findByProjectId(projectId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public QueueResponse pause(Long queueId) {
        Queue queue = getValidatedQueue(queueId);
        queue.setStatus(QueueStatus.PAUSED);
        return toResponse(queueRepository.save(queue));
    }

    public QueueResponse resume(Long queueId) {
        Queue queue = getValidatedQueue(queueId);
        queue.setStatus(QueueStatus.ACTIVE);
        return toResponse(queueRepository.save(queue));
    }

    public void delete(Long queueId) {
        Queue queue = getValidatedQueue(queueId);
        queueRepository.delete(queue);
    }

    // Used by WorkerService — package-level access
    public Queue findById(Long id) {
        return queueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found"));
    }

    private Project getValidatedProject(Long projectId) {
        User user = currentUserProvider.getCurrentUser();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return project;
    }

    private Queue getValidatedQueue(Long queueId) {
        User user = currentUserProvider.getCurrentUser();
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue not found"));
        if (!queue.getProject().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return queue;
    }

    private QueueResponse toResponse(Queue q) {
        return new QueueResponse(q.getId(), q.getName(), q.getPriority(),
                q.getConcurrencyLimit(), q.getStatus(), q.getMaxRetries(),
                q.getRetryType(), q.getBaseDelaySeconds(), q.getCreatedAt());
    }
}
