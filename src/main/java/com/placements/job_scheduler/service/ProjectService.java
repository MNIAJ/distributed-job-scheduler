package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateProjectRequest;
import com.placements.job_scheduler.dto.response.ProjectResponse;
import com.placements.job_scheduler.entity.Project;
import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.exception.ResourceNotFoundException;
import com.placements.job_scheduler.repository.ProjectRepository;
import com.placements.job_scheduler.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CurrentUserProvider currentUserProvider;

    public ProjectResponse create(CreateProjectRequest request) {
        User user = currentUserProvider.getCurrentUser();

        Project project = Project.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    public List<ProjectResponse> getAll() {
        User user = currentUserProvider.getCurrentUser();
        return projectRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProjectResponse getById(Long id) {
        Project project = findAndValidateOwnership(id);
        return toResponse(project);
    }

    public ProjectResponse update(Long id, CreateProjectRequest request) {
        Project project = findAndValidateOwnership(id);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        return toResponse(projectRepository.save(project));
    }

    public void delete(Long id) {
        Project project = findAndValidateOwnership(id);
        projectRepository.delete(project);
    }

    // Private helpers
    private Project findAndValidateOwnership(Long id) {
        User user = currentUserProvider.getCurrentUser();
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return project;
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(p.getId(), p.getName(),
                p.getDescription(), p.getCreatedAt());
    }
}