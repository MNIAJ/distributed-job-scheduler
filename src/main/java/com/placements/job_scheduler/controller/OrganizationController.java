package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.request.CreateOrganizationRequest;
import com.placements.job_scheduler.dto.response.OrganizationResponse;
import com.placements.job_scheduler.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> getAll() {
        return ResponseEntity.ok(organizationService.getAll());
    }
}