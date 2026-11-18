package com.ai.aiops.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.agent.RootCauseAnalysisService;
import com.ai.aiops.api.dto.AgentExecutionResponse;
import com.ai.aiops.api.dto.IncidentResponse;
import com.ai.aiops.api.dto.RcaResponse;
import com.ai.aiops.api.dto.RemediationRecommendationResponse;
import com.ai.aiops.dashboard.IncidentQueryService;
import com.ai.aiops.remediation.RemediationRecommendationService;
import com.ai.aiops.repository.AgentExecutionRepository;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentQueryService incidentQueryService;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final RemediationRecommendationService remediationRecommendationService;
    private final AgentExecutionRepository agentExecutionRepository;

    public IncidentController(IncidentQueryService incidentQueryService,
                               RootCauseAnalysisService rootCauseAnalysisService,
                               RemediationRecommendationService remediationRecommendationService,
                               AgentExecutionRepository agentExecutionRepository) {
        this.incidentQueryService = incidentQueryService;
        this.rootCauseAnalysisService = rootCauseAnalysisService;
        this.remediationRecommendationService = remediationRecommendationService;
        this.agentExecutionRepository = agentExecutionRepository;
    }

    @GetMapping
    public Page<IncidentResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return incidentQueryService.list(status, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable UUID id) {
        return incidentQueryService.getById(id);
    }

    @PostMapping("/{id}/investigate")
    public RcaResponse investigate(@PathVariable UUID id) {
        return rootCauseAnalysisService.investigate(id);
    }

    @GetMapping("/{id}/analysis")
    public RcaResponse getAnalysis(@PathVariable UUID id) {
        return rootCauseAnalysisService.getLatestAnalysis(id);
    }

    @PostMapping("/{id}/recommendations")
    public RemediationRecommendationResponse recommend(@PathVariable UUID id) {
        return remediationRecommendationService.recommend(id);
    }

    @GetMapping("/{id}/recommendations")
    public List<RemediationRecommendationResponse> listRecommendations(@PathVariable UUID id) {
        return remediationRecommendationService.listForIncident(id);
    }

    @GetMapping("/{id}/agent-executions")
    public Page<AgentExecutionResponse> agentExecutions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return agentExecutionRepository.findByIncidentIdOrderByCreatedAtDesc(id, PageRequest.of(page, size))
                .map(AgentExecutionResponse::from);
    }
}
