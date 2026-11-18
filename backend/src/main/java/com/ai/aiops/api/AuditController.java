package com.ai.aiops.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.AgentExecutionResponse;
import com.ai.aiops.api.dto.OperationalAuditResponse;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.OperationalAuditRepository;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final OperationalAuditRepository operationalAuditRepository;
    private final AgentExecutionRepository agentExecutionRepository;

    public AuditController(OperationalAuditRepository operationalAuditRepository,
                            AgentExecutionRepository agentExecutionRepository) {
        this.operationalAuditRepository = operationalAuditRepository;
        this.agentExecutionRepository = agentExecutionRepository;
    }

    @GetMapping
    public Page<OperationalAuditResponse> operationalAudits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operationalAuditRepository.findAll(PageRequest.of(page, size)).map(OperationalAuditResponse::from);
    }

    @GetMapping("/agent-executions")
    public Page<AgentExecutionResponse> agentExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return agentExecutionRepository.findAll(PageRequest.of(page, size)).map(AgentExecutionResponse::from);
    }
}
