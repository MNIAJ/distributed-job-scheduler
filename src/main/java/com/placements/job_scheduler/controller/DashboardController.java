package com.placements.job_scheduler.controller;

import com.placements.job_scheduler.dto.response.DashboardResponse;
import com.placements.job_scheduler.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}
