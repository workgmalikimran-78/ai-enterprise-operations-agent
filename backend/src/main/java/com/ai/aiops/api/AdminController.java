package com.ai.aiops.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.execution.ExecutionGuard;

/** Emergency controls. POST /disable-execution immediately blocks every mutating K8s tool call. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final ExecutionGuard executionGuard;

    public AdminController(ExecutionGuard executionGuard) {
        this.executionGuard = executionGuard;
    }

    @PostMapping("/execution/disable")
    public Map<String, Object> disableExecution() {
        executionGuard.disableExecution();
        return Map.of("executionEnabled", executionGuard.isExecutionEnabled());
    }

    @PostMapping("/execution/enable")
    public Map<String, Object> enableExecution() {
        executionGuard.enableExecution();
        return Map.of("executionEnabled", executionGuard.isExecutionEnabled());
    }

    @GetMapping("/execution/status")
    public Map<String, Object> executionStatus() {
        return Map.of("executionEnabled", executionGuard.isExecutionEnabled());
    }
}
