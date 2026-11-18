package com.ai.aiops.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.DashboardMetricsResponse;
import com.ai.aiops.api.dto.DashboardOverviewResponse;
import com.ai.aiops.api.dto.IncidentResponse;
import com.ai.aiops.dashboard.DashboardService;
import com.ai.aiops.dashboard.IncidentQueryService;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final IncidentQueryService incidentQueryService;

    public DashboardController(DashboardService dashboardService, IncidentQueryService incidentQueryService) {
        this.dashboardService = dashboardService;
        this.incidentQueryService = incidentQueryService;
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        return dashboardService.overview();
    }

    @GetMapping("/metrics")
    public DashboardMetricsResponse metrics() {
        return dashboardService.metrics();
    }

    @GetMapping("/incidents")
    public Page<IncidentResponse> recentIncidents(@RequestParam(defaultValue = "10") int size) {
        return incidentQueryService.list(null, PageRequest.of(0, size));
    }
}
