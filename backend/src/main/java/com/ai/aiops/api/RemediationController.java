package com.ai.aiops.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.RemediationExecutionResponse;
import com.ai.aiops.execution.RemediationExecutionService;

@RestController
@RequestMapping("/api/v1/remediations")
public class RemediationController {

    private final RemediationExecutionService executionService;

    public RemediationController(RemediationExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/{id}/execute")
    public RemediationExecutionResponse execute(@PathVariable UUID id) {
        return executionService.execute(id);
    }

    @GetMapping("/{id}/status")
    public RemediationExecutionResponse status(@PathVariable UUID id) {
        return executionService.getStatus(id);
    }
}
