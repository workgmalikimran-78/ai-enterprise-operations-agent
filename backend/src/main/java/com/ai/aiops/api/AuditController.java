package com.ai.aiops.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.AgentExecutionResponse;
import com.ai.aiops.api.dto.OperationalAuditResponse;
import com.ai.aiops.dashboard.AuditQueryService;
import com.ai.aiops.repository.AgentExecutionRepository;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final AgentExecutionRepository agentExecutionRepository;

    public AuditController(AuditQueryService auditQueryService, AgentExecutionRepository agentExecutionRepository) {
        this.auditQueryService = auditQueryService;
        this.agentExecutionRepository = agentExecutionRepository;
    }

    @GetMapping
    public Page<OperationalAuditResponse> operationalAudits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.list(PageRequest.of(page, size));
    }

    @GetMapping("/agent-executions")
    public Page<AgentExecutionResponse> agentExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return agentExecutionRepository.findAll(PageRequest.of(page, size)).map(AgentExecutionResponse::from);
    }
}
