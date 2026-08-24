package com.placements.job_scheduler.service;

import com.placements.job_scheduler.dto.request.CreateOrganizationRequest;
import com.placements.job_scheduler.dto.response.OrganizationResponse;
import com.placements.job_scheduler.entity.Organization;
import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.repository.OrganizationRepository;
import com.placements.job_scheduler.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final CurrentUserProvider currentUserProvider;

    public OrganizationResponse create(CreateOrganizationRequest request) {
        User user = currentUserProvider.getCurrentUser();
        Organization org = Organization.builder()
                .owner(user)
                .name(request.getName())
                .description(request.getDescription())
                .build();
        Organization saved = organizationRepository.save(org);
        return toResponse(saved);
    }

    public List<OrganizationResponse> getAll() {
        User user = currentUserProvider.getCurrentUser();
        return organizationRepository.findByOwnerId(user.getId())
                .stream().map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OrganizationResponse toResponse(Organization o) {
        return new OrganizationResponse(o.getId(), o.getName(),
                o.getDescription(), o.getCreatedAt());
    }
}
